package com.fiscalhub.backend.relatorio;

import com.fiscalhub.backend.contabil.DemonstracaoService;
import com.fiscalhub.backend.fiscal.ApuracaoService;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.OrigemValor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Carga tributária efetiva por exercício, sem estimativa: só divide o que foi importado.
 * <ul>
 *   <li>receita bruta (3.01.01.01.01) e resultado antes do IRPJ e da CSLL (3.01) da DRE referencial da ECD ou, sem ela,
 *   do L300/P150 da ECF (soma dos períodos);</li>
 *   <li>tributos: valor a recolher das apurações vigentes do exercício (após créditos, deduções e estimativas).</li>
 * </ul>
 * Uma razão só sai com todos os insumos: a receita (ou o LAIR positivo) e os 12 meses de cada escrituração fiscal que o
 * cliente tem (EFD-Contribuições, EFD ICMS/IPI, ECF). Faltou algo: a razão fica nula e "faltando" diz o que importar.
 */
@Service
@Transactional(readOnly = true)
public class CargaTributariaService {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final Map<String, String> ESCRITURACOES = Map.of("EFD_CONTRIBUICOES", "EFD-Contribuições",
            "EFD_ICMS_IPI", "EFD ICMS/IPI", "ECF", "ECF");

    public record TributoCarga(String tributo, BigDecimal valor, BigDecimal carga) {
    }

    /**
     * carga e cargaIrpjCsll em % (12.5 = 12,5%); nulas quando falta insumo. irpjCsllNaoAplicavel: LAIR ≤ 0 (prejuízo).
     * documentos: ids dos documentos de onde vieram os números (anexo de fontes).
     */
    public record ExercicioCarga(int exercicio, BigDecimal receitaBruta, OrigemValor origemReceita, BigDecimal lair,
                                 OrigemValor origemLair, BigDecimal tributos, BigDecimal carga, BigDecimal irpjCsll,
                                 BigDecimal cargaIrpjCsll, boolean irpjCsllNaoAplicavel, List<String> faltando,
                                 List<TributoCarga> porTributo, Set<Long> documentos) {
    }

    private final DemonstracaoService demonstracoes;
    private final ApuracaoService apuracoes;
    private final JdbcTemplate jdbc;

    public CargaTributariaService(DemonstracaoService demonstracoes, ApuracaoService apuracoes, JdbcTemplate jdbc) {
        this.demonstracoes = demonstracoes;
        this.apuracoes = apuracoes;
        this.jdbc = jdbc;
    }

    /** de/ate: exercícios, inclusive (nulos = todos com receita ou apuração). */
    public List<ExercicioCarga> carga(long clienteId, Integer de, Integer ate, UsuarioAutenticado ua) {
        List<ApuracaoService.Apuracao> todas = apuracoes.apuracoes(clienteId, null, null, null, ua);
        DemonstracaoService.Comparativo dre = demonstracoes.comparativo(clienteId, "DRE", "REFERENCIAL", null, ua);
        Set<String> escrituracoesDoCliente = todas.stream().map(ApuracaoService.Apuracao::escrituracao)
                .filter(ESCRITURACOES::containsKey).collect(Collectors.toCollection(TreeSet::new));
        Set<Integer> anos = new TreeSet<>();
        dre.exercicios().forEach(e -> anos.add(e.exercicio()));
        todas.forEach(a -> anos.add(a.dataFim().getYear()));
        anos.addAll(exerciciosEcf(clienteId));
        List<ExercicioCarga> resultado = new ArrayList<>();
        for (int ano : anos) {
            if ((de == null || ano >= de) && (ate == null || ano <= ate)) {
                resultado.add(exercicio(clienteId, ano, dre, todas, escrituracoesDoCliente));
            }
        }
        return resultado;
    }

    private ExercicioCarga exercicio(long clienteId, int ano, DemonstracaoService.Comparativo dre,
                                     List<ApuracaoService.Apuracao> todas, Set<String> escrituracoesDoCliente) {
        Set<Long> documentos = new LinkedHashSet<>();
        List<String> faltando = new ArrayList<>();
        Linha receita = linha(clienteId, dre, ano, "3.01.01.01.01");
        Linha lair = linha(clienteId, dre, ano, "3.01");
        if (receita == null) {
            faltando.add("receita bruta de " + ano + ": importe a ECD (ou a ECF) do exercício");
        } else {
            documentos.add(receita.origem().documentoId());
        }
        if (lair != null) {
            documentos.add(lair.origem().documentoId());
        }

        List<ApuracaoService.Apuracao> doAno = todas.stream().filter(a -> a.dataFim().getYear() == ano).toList();
        doAno.forEach(a -> documentos.add(a.documentoId()));
        Map<String, BigDecimal> porTributo = new TreeMap<>();
        doAno.forEach(a -> porTributo.merge(a.tributo(), nz(a.valorARecolher()), BigDecimal::add));
        Set<String> incompletas = new TreeSet<>();
        for (String escrituracao : escrituracoesDoCliente) {
            List<YearMonth> meses = mesesFaltando(doAno.stream().filter(a -> escrituracao.equals(a.escrituracao())).toList(), ano);
            if (!meses.isEmpty()) {
                incompletas.add(escrituracao);
                faltando.add(ESCRITURACOES.get(escrituracao) + ": importe " + (meses.size() == 12 ? "o exercício de " + ano
                        : "os meses " + meses.stream().map(m -> String.format("%02d/%d", m.getMonthValue(), m.getYear()))
                        .collect(Collectors.joining(", "))));
            }
        }
        Map<String, String> escrituracaoDoTributo = doAno.stream()
                .collect(Collectors.toMap(ApuracaoService.Apuracao::tributo, ApuracaoService.Apuracao::escrituracao, (a, b) -> a));
        BigDecimal receitaBruta = receita == null ? null : receita.valor();
        List<TributoCarga> tributos = porTributo.entrySet().stream()
                .map(t -> new TributoCarga(t.getKey(), t.getValue(),
                        incompletas.contains(escrituracaoDoTributo.get(t.getKey())) ? null : razao(t.getValue(), receitaBruta)))
                .toList();
        BigDecimal total = porTributo.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean completo = incompletas.isEmpty() && !escrituracoesDoCliente.isEmpty();
        if (escrituracoesDoCliente.isEmpty()) {
            faltando.add("tributos: importe as escriturações fiscais (EFD-Contribuições, EFD ICMS/IPI, ECF)");
        }

        BigDecimal irpjCsll = porTributo.containsKey("IRPJ") || porTributo.containsKey("CSLL")
                ? nz(porTributo.get("IRPJ")).add(nz(porTributo.get("CSLL"))) : null;
        boolean ecfCompleta = escrituracoesDoCliente.contains("ECF") && !incompletas.contains("ECF");
        boolean naoAplicavel = lair != null && lair.valor().signum() <= 0;
        if (lair == null) {
            faltando.add("lucro antes do IRPJ e da CSLL de " + ano + ": importe a ECD (ou a ECF) do exercício");
        } else if (!escrituracoesDoCliente.contains("ECF")) {
            faltando.add("IRPJ e CSLL de " + ano + ": importe a ECF do exercício");
        }
        BigDecimal cargaIrpjCsll = ecfCompleta && !naoAplicavel && irpjCsll != null ? razao(irpjCsll, lair == null ? null : lair.valor()) : null;
        return new ExercicioCarga(ano, receitaBruta, receita == null ? null : receita.origem(), lair == null ? null : lair.valor(),
                lair == null ? null : lair.origem(), doAno.isEmpty() ? null : total, completo ? razao(total, receitaBruta) : null,
                irpjCsll, cargaIrpjCsll, naoAplicavel, faltando, tributos, documentos);
    }

    private record Linha(BigDecimal valor, OrigemValor origem) {
    }

    /** Da DRE referencial (credor negativo: vira positivo) ou, sem ela, da ECF. */
    private Linha linha(long clienteId, DemonstracaoService.Comparativo dre, int ano, String codigo) {
        DemonstracaoService.Exercicio e = dre.exercicios().stream().filter(x -> x.exercicio() == ano).findFirst().orElse(null);
        if (e != null) {
            for (DemonstracaoService.LinhaComparativa l : dre.linhas()) {
                if (l.codigo().equals(codigo) && l.valores().get(ano) != null && l.valores().get(ano).valor() != null) {
                    return new Linha(l.valores().get(ano).valor().negate(), new OrigemValor(e.documentoId(), e.arquivo(), e.origemDados(),
                            null, "DRE referencial " + codigo, null, l.descricao()));
                }
            }
        }
        return linhaEcf(clienteId, ano, codigo);
    }

    /** L300 (real) ou P150 (presumido) da ECF vigente do exercício, somados os períodos de apuração. */
    private Linha linhaEcf(long clienteId, int ano, String codigo) {
        List<Map<String, Object>> linhas = jdbc.queryForList("""
                SELECT l.valor_final, l.registro, l.linha, l.descricao, e.documento_id, doc.nome_original_arquivo
                FROM linha_demonstracao l
                JOIN demonstracao d ON d.id = l.demonstracao_id
                JOIN escrituracao_contabil e ON e.id = d.escrituracao_id AND e.situacao = 'VIGENTE' AND e.origem = 'ECF'
                JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE e.cliente_id = ? AND extract(YEAR FROM e.data_fim) = ? AND d.origem = 'ECF' AND l.codigo = ?
                ORDER BY l.linha""", clienteId, ano, codigo);
        if (linhas.isEmpty()) {
            return null;
        }
        BigDecimal soma = linhas.stream().map(m -> (BigDecimal) m.get("valor_final")).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> p = linhas.getFirst();
        return new Linha(soma.negate(), new OrigemValor(((Number) p.get("documento_id")).longValue(), (String) p.get("nome_original_arquivo"),
                "ECF", null, (String) p.get("registro"), ((Number) p.get("linha")).longValue(), (String) p.get("descricao")));
    }

    private List<Integer> exerciciosEcf(long clienteId) {
        return jdbc.queryForList("""
                SELECT DISTINCT extract(YEAR FROM data_fim)::int FROM escrituracao_contabil
                WHERE cliente_id = ? AND origem = 'ECF' AND situacao = 'VIGENTE'""", Integer.class, clienteId);
    }

    /** Meses do exercício sem nenhuma apuração da escrituração (apuração trimestral ou anual cobre os seus meses). */
    static List<YearMonth> mesesFaltando(List<ApuracaoService.Apuracao> apuracoes, int ano) {
        Set<YearMonth> cobertos = new TreeSet<>();
        for (ApuracaoService.Apuracao a : apuracoes) {
            for (YearMonth m = YearMonth.from(a.dataInicio()); !m.isAfter(YearMonth.from(a.dataFim())); m = m.plusMonths(1)) {
                cobertos.add(m);
            }
        }
        List<YearMonth> faltando = new ArrayList<>();
        for (int mes = 1; mes <= 12; mes++) {
            if (!cobertos.contains(YearMonth.of(ano, mes))) {
                faltando.add(YearMonth.of(ano, mes));
            }
        }
        return faltando;
    }

    private static BigDecimal razao(BigDecimal valor, BigDecimal base) {
        return valor == null || base == null || base.signum() <= 0 ? null : valor.multiply(CEM).divide(base, 2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
