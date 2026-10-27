package com.fiscalhub.backend.situacao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.SituacaoDebito;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.fiscal.RepositorioApuracao;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.AcessoRecursoService;
import com.fiscalhub.backend.situacao.AcrescimosMoratorios.Acrescimos;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.OrigemValor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Conciliação apurado × declarado × pago, por cliente + esfera + tributo (+ UF no estadual) + período de apuração. O
 * código de receita de declarações e pagamentos vira tributo pela tabela de códigos de receita (CodigosReceita).
 * <ul>
 *   <li>apurado: escriturações fiscais vigentes (EFD-Contribuições, EFD ICMS/IPI, ECF), valor a recolher;</li>
 *   <li>declarado: DCTF, DCTFWeb; no ICMS, a conta corrente do PR (o que a EFD declarou) ou, sem ela, o próprio apurado
 *   (a EFD é a declaração do ICMS);</li>
 *   <li>pago: DARF, relatório de pagamentos, GR-PR (parcela de parcelamento fica de fora: vai para o parcelamento).</li>
 * </ul>
 * Calculada na consulta (sempre igual aos documentos vigentes); cada diferença aponta o débito correspondente na
 * situação fiscal (RFB, SEFA-PR) e diferença sem débito, ou débito sem diferença, vira alerta.
 */
@Service
@Transactional(readOnly = true)
public class ConciliacaoService {

    public enum Status {
        CONCILIADO, DIVERGENCIA_APURADO_DECLARADO, NAO_DECLARADO, PAGO_A_MENOR, PAGO_A_MAIOR, NAO_PAGO,
        PAGO_EM_ATRASO_SEM_ACRESCIMOS, COMPENSADO_OU_SUSPENSO, PARCELADO
    }

    /** Status de diferença que deve ter débito na situação fiscal. */
    private static final Set<Status> COM_DEBITO = Set.of(Status.NAO_PAGO, Status.PAGO_A_MENOR, Status.PAGO_EM_ATRASO_SEM_ACRESCIMOS);

    public record Valor(BigDecimal valor, List<OrigemValor> origens) {
    }

    /** O que foi pago: principal, acréscimos pagos e a data do último pagamento. */
    public record Pago(BigDecimal valor, BigDecimal multa, BigDecimal juros, LocalDate dataPagamento, List<OrigemValor> origens) {
    }

    public record DebitoRelacionado(long id, OrgaoCredor orgao, SituacaoDebito situacao, BigDecimal valorTotal,
                                    boolean impedeCertidao, List<OrigemValor> origens) {
    }

    /**
     * Um item da conciliação. diferenca: devido − pago (positivo = falta pagar); na divergência, apurado − declarado.
     * acrescimos: devidos pelo atraso (federal), até o pagamento ou, se não pago, até a data de cálculo.
     */
    public record Item(String id, String esfera, TipoTributo tributo, String uf, YearMonth periodo, LocalDate vencimento,
                       Status status, Valor apurado, Valor declarado, Pago pago, BigDecimal diferenca, Acrescimos acrescimos,
                       String erroAcrescimos, List<String> codigosReceita, List<DebitoRelacionado> debitos) {
    }

    public record Alerta(String regra, String itemId, Long debitoId, String mensagem) {
    }

    public record Conciliacao(BigDecimal tolerancia, LocalDate dataCalculo, List<Item> itens, List<Alerta> alertas) {
    }

    private final JdbcTemplate jdbc;
    private final AcessoRecursoService acesso;
    private final SituacaoFiscalService situacao;
    private final AcrescimosMoratorios acrescimos;
    private final ObjectMapper json;
    private final BigDecimal tolerancia;

    public ConciliacaoService(JdbcTemplate jdbc, AcessoRecursoService acesso, SituacaoFiscalService situacao,
                              AcrescimosMoratorios acrescimos, ObjectMapper json,
                              @Value("${fiscalhub.conciliacao.tolerancia:0.01}") BigDecimal tolerancia) {
        this.jdbc = jdbc;
        this.acesso = acesso;
        this.situacao = situacao;
        this.acrescimos = acrescimos;
        this.json = json;
        this.tolerancia = tolerancia;
    }

    public Conciliacao conciliar(long clienteId, YearMonth de, YearMonth ate, Status status, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        Conciliacao c = conciliar(clienteId);
        List<Item> itens = c.itens().stream()
                .filter(i -> de == null || !i.periodo().isBefore(de))
                .filter(i -> ate == null || !i.periodo().isAfter(ate))
                .filter(i -> status == null || i.status() == status).toList();
        Set<String> ids = itens.stream().map(Item::id).collect(java.util.stream.Collectors.toSet());
        return new Conciliacao(c.tolerancia(), c.dataCalculo(), itens,
                c.alertas().stream().filter(a -> a.itemId() == null || ids.contains(a.itemId())).toList());
    }

    public Item item(long clienteId, String itemId, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return conciliar(clienteId).itens().stream().filter(i -> i.id().equals(itemId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item da conciliação não encontrado: " + itemId));
    }

    /** Montagem dos itens (chave -> apurado, declarado, pago) e do status de cada um. */
    Conciliacao conciliar(long clienteId) {
        Map<String, Montagem> itens = new TreeMap<>();
        apurado(clienteId, itens);
        boolean temContaCorrente = false;
        for (SituacaoFiscalService.Declarado d : situacao.declaracoes(clienteId)) {
            if (d.tributo() == null) {
                continue;
            }
            temContaCorrente |= "CONTA_CORRENTE_PR".equals(d.declaracao());
            Montagem m = montagem(itens, d.tributo(), uf(d.tributo()), YearMonth.from(d.periodo()));
            m.declarado = soma(m.declarado, d.valor());
            m.vinculadoCompensadoSuspenso = soma(m.vinculadoCompensadoSuspenso, soma(d.valorCompensado(), d.valorSuspenso()));
            m.vinculadoParcelado = soma(m.vinculadoParcelado, d.valorParcelado());
            m.origensDeclarado.addAll(d.origens());
            m.vencimento = min(m.vencimento, d.vencimento());
            m.codigos.add(d.codigoReceita());
        }
        for (SituacaoFiscalService.Pagamento p : situacao.pagamentos(clienteId)) {
            if (p.tributo() == null || p.parcelamento() != null || p.periodo() == null) {
                continue; // sem código (DARF DCTFWeb só com o total) ou parcela de parcelamento
            }
            Montagem m = montagem(itens, p.tributo(), uf(p.tributo()), YearMonth.from(p.periodo()));
            m.pago = soma(m.pago, p.principal() != null ? p.principal() : p.total());
            m.multaPaga = soma(m.multaPaga, p.multa());
            m.jurosPagos = soma(m.jurosPagos, p.juros());
            m.dataPagamento = m.dataPagamento == null || p.dataPagamento().isAfter(m.dataPagamento) ? p.dataPagamento() : m.dataPagamento;
            m.vencimento = m.vencimento == null ? p.vencimento() : m.vencimento;
            m.origensPago.addAll(p.origens());
            m.codigos.add(p.codigoReceita());
        }
        List<SituacaoFiscalService.Debito> debitos = situacao.debitos(clienteId);
        LocalDate hoje = LocalDate.now();
        List<Item> resultado = new ArrayList<>();
        List<Alerta> alertas = new ArrayList<>();
        for (Montagem m : itens.values()) {
            if ("ESTADUAL".equals(m.esfera) && m.declarado == null && !temContaCorrente && m.apurado != null) {
                m.declarado = m.apurado; // sem a conta corrente do PR, a EFD (apurado) é a declaração do ICMS
                m.origensDeclarado.addAll(m.origensApurado);
            }
            Item item = item(m, debitos, hoje);
            resultado.add(item);
            if (COM_DEBITO.contains(item.status()) && item.debitos().isEmpty()) {
                alertas.add(new Alerta("DIFERENCA_SEM_DEBITO", item.id(), null, rotulo(item) + ": " + item.status()
                        + " de " + item.diferenca().toPlainString() + " sem débito correspondente na situação fiscal "
                        + "(relatório de situação fiscal ou conta corrente do PR). Importe o relatório mais recente ou confira."));
            }
        }
        for (SituacaoFiscalService.Debito d : debitos) {
            if (Set.of(OrgaoCredor.RFB, OrgaoCredor.SEFA_PR).contains(d.orgaoCredor()) && d.competencia() != null
                    && d.situacao() == SituacaoDebito.EM_ABERTO && resultado.stream().noneMatch(i -> COM_DEBITO.contains(i.status())
                    && i.debitos().stream().anyMatch(r -> r.id() == d.id()))) {
                alertas.add(new Alerta("DEBITO_SEM_DIFERENCA", null, d.id(), "Débito de " + d.tributo() + " "
                        + YearMonth.from(d.competencia()) + " em aberto (" + d.orgaoCredor() + ", "
                        + d.valorTotal().toPlainString() + ") sem diferença na conciliação que o explique: faltam o apurado, "
                        + "o declarado ou o pago do período, ou o débito já foi pago depois do relatório."));
            }
        }
        resultado.sort(Comparator.comparing(Item::periodo).reversed().thenComparing(Item::esfera).thenComparing(Item::tributo));
        return new Conciliacao(tolerancia, hoje, resultado, alertas);
    }

    private Item item(Montagem m, List<SituacaoFiscalService.Debito> debitos, LocalDate hoje) {
        BigDecimal devido = m.declarado != null ? m.declarado : m.apurado != null ? m.apurado : BigDecimal.ZERO;
        BigDecimal pago = m.pago == null ? BigDecimal.ZERO : m.pago;
        BigDecimal diferenca = devido.subtract(pago);
        List<DebitoRelacionado> relacionados = debitos.stream()
                .filter(d -> d.tributo() == m.tributo && d.competencia() != null && YearMonth.from(d.competencia()).equals(m.periodo)
                        && Set.of(OrgaoCredor.RFB, OrgaoCredor.SEFA_PR).contains(d.orgaoCredor()))
                .map(d -> new DebitoRelacionado(d.id(), d.orgaoCredor(), d.situacao(), d.valorTotal(), d.impedeCertidao(),
                        d.origens().stream().map(SituacaoFiscalService.OrigemDebito::origem).toList()))
                .toList();
        Status status = classificar(m.apurado, m.declarado, m.pago, m.vinculadoCompensadoSuspenso, m.vinculadoParcelado,
                relacionados.stream().anyMatch(d -> d.situacao() == SituacaoDebito.PARCELADO), tolerancia);
        if (status == Status.NAO_DECLARADO) {
            diferenca = m.apurado;
        } else if (status == Status.DIVERGENCIA_APURADO_DECLARADO) {
            diferenca = m.apurado.subtract(m.declarado);
        }
        Acrescimos devidos = null;
        String erro = null;
        boolean federal = "FEDERAL".equals(m.esfera);
        // acréscimos de mora (regra federal): pago depois do vencimento, ou ainda em aberto depois dele
        if (federal && m.vencimento != null) {
            try {
                if (status == Status.CONCILIADO && m.dataPagamento != null && m.dataPagamento.isAfter(m.vencimento)) {
                    devidos = acrescimos.calcular(pago, m.vencimento, m.dataPagamento);
                    BigDecimal pagos = soma(m.multaPaga, m.jurosPagos);
                    if (devidos.total().subtract(pagos == null ? BigDecimal.ZERO : pagos).compareTo(tolerancia) > 0) {
                        status = Status.PAGO_EM_ATRASO_SEM_ACRESCIMOS;
                        diferenca = devidos.total().subtract(pagos == null ? BigDecimal.ZERO : pagos);
                    }
                } else if ((status == Status.NAO_PAGO || status == Status.PAGO_A_MENOR) && hoje.isAfter(m.vencimento)) {
                    devidos = acrescimos.calcular(diferenca, m.vencimento, hoje);
                }
            } catch (BusinessRuleException e) {
                erro = e.getMessage() + " Carregue a SELIC para calcular os acréscimos.";
            }
        }
        return new Item(m.id(), m.esfera, m.tributo, m.uf, m.periodo, m.vencimento, status,
                m.apurado == null ? null : new Valor(m.apurado, m.origensApurado),
                m.declarado == null ? null : new Valor(m.declarado, m.origensDeclarado),
                m.pago == null ? null : new Pago(m.pago, m.multaPaga, m.jurosPagos, m.dataPagamento, m.origensPago),
                diferenca, devidos, erro, m.codigos.stream().filter(Objects::nonNull).distinct().sorted().toList(), relacionados);
    }

    /**
     * Status pelos três valores, na ordem: vinculação (compensação/suspensão, parcelamento), apurado × declarado, depois
     * declarado (ou apurado) × pago. O atraso sem acréscimos é decidido depois, com os acréscimos devidos.
     */
    static Status classificar(BigDecimal apurado, BigDecimal declarado, BigDecimal pago, BigDecimal compensadoOuSuspenso,
                              BigDecimal parcelado, boolean debitoParcelado, BigDecimal tolerancia) {
        if (compensadoOuSuspenso != null && compensadoOuSuspenso.signum() > 0) {
            return Status.COMPENSADO_OU_SUSPENSO;
        }
        if ((parcelado != null && parcelado.signum() > 0) || debitoParcelado) {
            return Status.PARCELADO;
        }
        if (apurado != null && declarado == null && apurado.compareTo(tolerancia) > 0) {
            return Status.NAO_DECLARADO;
        }
        if (apurado != null && declarado != null && apurado.subtract(declarado).abs().compareTo(tolerancia) > 0) {
            return Status.DIVERGENCIA_APURADO_DECLARADO;
        }
        BigDecimal devido = declarado != null ? declarado : apurado != null ? apurado : BigDecimal.ZERO;
        BigDecimal valorPago = pago == null ? BigDecimal.ZERO : pago;
        BigDecimal diferenca = devido.subtract(valorPago);
        if (devido.compareTo(tolerancia) > 0 && valorPago.compareTo(tolerancia) <= 0) {
            return Status.NAO_PAGO;
        }
        if (diferenca.compareTo(tolerancia) > 0) {
            return Status.PAGO_A_MENOR;
        }
        return diferenca.negate().compareTo(tolerancia) > 0 ? Status.PAGO_A_MAIOR : Status.CONCILIADO;
    }

    /** Valor a recolher das escriturações fiscais vigentes, com a origem (registro e linha) do valor. */
    private void apurado(long clienteId, Map<String, Montagem> itens) {
        jdbc.query("""
                SELECT a.tributo, a.esfera, a.uf, a.data_fim, a.valor_a_recolher, a.origem::text, a.documento_id,
                       d.nome_original_arquivo, d.tipo
                FROM apuracao_tributo a
                JOIN escrituracao_contabil e ON e.id = a.escrituracao_id AND e.situacao = 'VIGENTE'
                JOIN documento_fiscal d ON d.id = a.documento_id
                WHERE a.cliente_id = ?""", rs -> {
            TipoTributo tributo = TipoTributo.valueOf(rs.getString(1));
            Montagem m = montagem(itens, tributo, rs.getString(3), YearMonth.from(rs.getDate(4).toLocalDate()));
            m.apurado = soma(m.apurado, rs.getBigDecimal(5));
            Map<String, RepositorioApuracao.Origem> origem = origens(rs.getString(6));
            RepositorioApuracao.Origem o = origem.getOrDefault("valorARecolher", origem.values().stream().findFirst().orElse(null));
            if (o != null) {
                m.origensApurado.add(new OrigemValor(rs.getLong(7), rs.getString(8), rs.getString(9), null, o.registro(), o.linha(), null));
            }
        }, clienteId);
        jdbc.query("""
                SELECT a.tributo, a.uf, a.data_fim, min(o.vencimento) FROM obrigacao_recolher o
                JOIN apuracao_tributo a ON a.id = o.apuracao_id
                JOIN escrituracao_contabil e ON e.id = a.escrituracao_id AND e.situacao = 'VIGENTE'
                WHERE a.cliente_id = ? GROUP BY a.tributo, a.uf, a.data_fim""", rs -> {
            Montagem m = itens.get(chave(TipoTributo.valueOf(rs.getString(1)), rs.getString(2),
                    YearMonth.from(rs.getDate(3).toLocalDate())));
            if (m != null && rs.getDate(4) != null) {
                m.vencimento = min(m.vencimento, rs.getDate(4).toLocalDate());
            }
        }, clienteId);
    }

    private Map<String, RepositorioApuracao.Origem> origens(String texto) {
        try {
            return json.readValue(texto, new TypeReference<>() { });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Estadual sem UF na declaração ou no pagamento: PR (os documentos estaduais lidos são do Paraná). */
    private static String uf(TipoTributo tributo) {
        return CodigosReceita.codigo(tributo, "ESTADUAL").isPresent() ? "PR" : null;
    }

    private static Montagem montagem(Map<String, Montagem> itens, TipoTributo tributo, String uf, YearMonth periodo) {
        return itens.computeIfAbsent(chave(tributo, uf, periodo), k -> new Montagem(tributo, uf, periodo));
    }

    private static String chave(TipoTributo tributo, String uf, YearMonth periodo) {
        return tributo + (uf == null ? "" : "-" + uf) + "-" + periodo;
    }

    private static String rotulo(Item i) {
        return i.tributo() + (i.uf() == null ? "" : " " + i.uf()) + " " + i.periodo();
    }

    private static BigDecimal soma(BigDecimal a, BigDecimal b) {
        return a == null ? b : b == null ? a : a.add(b);
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a == null ? b : b == null || a.isBefore(b) ? a : b;
    }

    /** Item em montagem: os três valores somados por chave e as origens de cada um. */
    private static final class Montagem {
        final TipoTributo tributo;
        final String uf;
        final YearMonth periodo;
        final String esfera;
        BigDecimal apurado;
        BigDecimal declarado;
        BigDecimal pago;
        BigDecimal multaPaga;
        BigDecimal jurosPagos;
        BigDecimal vinculadoCompensadoSuspenso;
        BigDecimal vinculadoParcelado;
        LocalDate vencimento;
        LocalDate dataPagamento;
        final List<OrigemValor> origensApurado = new ArrayList<>();
        final List<OrigemValor> origensDeclarado = new ArrayList<>();
        final List<OrigemValor> origensPago = new ArrayList<>();
        final List<String> codigos = new ArrayList<>();

        Montagem(TipoTributo tributo, String uf, YearMonth periodo) {
            this.tributo = tributo;
            this.uf = uf;
            this.periodo = periodo;
            this.esfera = uf == null ? "FEDERAL" : "ESTADUAL";
        }

        String id() {
            return chave(tributo, uf, periodo);
        }
    }
}
