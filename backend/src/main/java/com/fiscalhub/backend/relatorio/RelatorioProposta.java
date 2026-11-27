package com.fiscalhub.backend.relatorio;

import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.parcelamento.MotorParcelamento;
import com.fiscalhub.backend.parcelamento.MotorParcelamento.Consolidado;
import com.fiscalhub.backend.parcelamento.MotorParcelamento.FonteUsada;
import com.fiscalhub.backend.parcelamento.MotorParcelamento.Parcela;
import com.fiscalhub.backend.parcelamento.MotorParcelamento.ResultadoModalidade;
import com.fiscalhub.backend.parcelamento.RegrasParcelamento;
import com.fiscalhub.backend.parcelamento.SimulacaoService;
import com.fiscalhub.backend.parcelamento.SimulacaoService.RecomendacaoOrgao;
import com.fiscalhub.backend.parcelamento.SimulacaoService.SimulacaoCompleta;
import com.fiscalhub.backend.relatorio.ModeloRelatorio.*;
import com.fiscalhub.backend.relatorio.MontagemRelatorios.Corpo;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

import static com.fiscalhub.backend.relatorio.MontagemRelatorios.colunas;
import static com.fiscalhub.backend.relatorio.MontagemRelatorios.secao;

/**
 * Proposta de regularização a partir de uma simulação salva (nada é recalculado: o resultado gravado é o da proposta):
 * recomendação por órgão, débitos com a origem, comparação das modalidades, cronograma e memória da recomendada,
 * resumo da capacidade, notas de risco e as fontes normativas com a versão das regras e dos índices.
 */
@Component
class RelatorioProposta {

    private final SimulacaoService simulacoes;
    private final RegrasParcelamento regras;

    RelatorioProposta(SimulacaoService simulacoes, RegrasParcelamento regras) {
        this.simulacoes = simulacoes;
        this.regras = regras;
    }

    Corpo proposta(long clienteId, Long simulacaoId, UsuarioAutenticado ua) {
        if (simulacaoId == null) {
            throw new BusinessRuleException("Escolha a simulação salva que vira proposta.");
        }
        SimulacaoCompleta s = simulacoes.detalhe(clienteId, simulacaoId, ua);
        if (s.motorAntigo() != null) {
            throw new BusinessRuleException("Simulação do motor antigo: recalcule antes de emitir a proposta.");
        }
        Set<Long> documentos = new LinkedHashSet<>();
        List<Secao> secoes = new ArrayList<>();
        Map<String, ResultadoModalidade> porChave = new LinkedHashMap<>();
        s.modalidades().forEach(m -> porChave.putIfAbsent(m.chave() + "|" + m.cenario(), m));
        List<ResultadoModalidade> recomendadas = s.recomendacao().stream().map(r -> porChave.get(r.chave() + "|" + r.cenario())).toList();

        secoes.add(secao("Recomendação", "Nenhuma modalidade elegível para os débitos escolhidos.", true, s.recomendacao(),
                colunas("Órgão", "Modalidade", "Custo projetado:MOEDA", "Economia × à vista:MOEDA", "Cabe na parcela sustentável", "Por quê"),
                r -> linha(r.orgao().name(), r.nome(), cru(r.custoProjetado()), cru(r.economiaVsAVista()),
                        r.cabe() == null ? "sem parcela sustentável" : r.cabe() ? "sim" : "não", r.justificativa()),
                List.of("Total", "", cru(s.custoRecomendado()), "", "", ""),
                "A de menor custo projetado entre as elegíveis que cabem na parcela sustentável (o cenário da CAPAG estimada nunca é recomendado)."));

        List<Consolidado> debitos = s.modalidades().stream().filter(m -> "BASE".equals(m.cenario())).flatMap(m -> m.debitos().stream()).toList();
        debitos.stream().filter(d -> d.origem() != null).forEach(d -> documentos.add(d.origem().documentoId()));
        secoes.add(secao("Débitos na data-base de " + Formatos.data(s.dataBase()), "Nenhum débito consolidado.", false, debitos,
                colunas("Débito", "Vencimento:DATA", "Principal:MOEDA", "Multa:MOEDA", "Juros:MOEDA", "Encargo:MOEDA", "Total:MOEDA", "Posição", "Origem"),
                d -> linha(d.descricao(), d.vencimento() == null ? null : d.vencimento().toString(), cru(d.principal()), cru(d.multa()),
                        cru(d.juros()), cru(d.encargo()), cru(d.total()), d.posicao(),
                        d.origem() == null ? "—" : d.origem().arquivo() + (d.origem().pagina() == null ? "" : ", p. " + d.origem().pagina())),
                null, null));

        List<ResultadoModalidade> comparadas = s.modalidades().stream().filter(m -> m.tipo() != RegrasParcelamento.Tipo.PROGRAMA).toList();
        secoes.add(secao("Comparação das modalidades", null, false, comparadas,
                colunas("Modalidade", "Cenário", "Elegível", "Total:MOEDA", "Desconto:MOEDA", "Entrada:MOEDA", "Prestações:INTEIRO",
                        "Parcela:MOEDA", "Custo nominal:MOEDA", "Custo projetado:MOEDA", "Economia × à vista:MOEDA"),
                m -> linha(m.nome(), Objects.toString(m.cenarioRotulo(), ""), m.elegivel() ? "sim" : m.bloqueio() != null ? "bloqueada" : "não",
                        cru(m.totalConsolidado()), m.desconto() == null ? null : cru(m.desconto().valor()), cru(m.entrada()),
                        String.valueOf(m.cronograma().size()), cru(m.valorParcela()), cru(m.custoNominal()), cru(m.custoProjetado()),
                        cru(m.economiaVsAVista())),
                null, "Custo projetado: com a SELIC conhecida até hoje e, nos meses seguintes, a " + MotorParcelamento.ROTULO_PROJECAO + "."));
        List<ResultadoModalidade> programas = s.modalidades().stream().filter(m -> m.tipo() == RegrasParcelamento.Tipo.PROGRAMA).toList();
        if (!programas.isEmpty()) {
            secoes.add(secao("Programas encerrados", null, false, programas, colunas("Programa", "Situação"),
                    m -> linha(m.nome(), m.bloqueio()), null, "Não aceitam adesão; ficam como histórico."));
        }

        for (ResultadoModalidade m : recomendadas) {
            String nome = m.nome() + (m.cenarioRotulo() == null ? "" : " (" + m.cenarioRotulo() + ")");
            secoes.add(secao("Cronograma — " + nome, null, false, m.cronograma(),
                    colunas("Nº:INTEIRO", "Tipo", "Vencimento:DATA", "Nominal:MOEDA", "Correção", "Valor:MOEDA", "SELIC", "Saldo nominal:MOEDA",
                            "Saldo projetado:MOEDA"),
                    p -> linha(String.valueOf(p.numero()), p.tipo().equals("ENTRADA") ? "entrada" : "parcela", p.vencimento().toString(),
                            cru(p.nominal()), percentual(p.percentualCorrecao()), cru(p.valor()), p.projetada() ? "projetada" : "conhecida",
                            cru(p.saldoNominal()), cru(p.saldoProjetado())),
                    List.of("Total", "", "", cru(m.custoNominal()), "", cru(m.custoProjetado()), "", "", ""), null));
            if (m.cronograma().size() > 1) {
                secoes.add(new Secao("Saldo nominal × projetado — " + nome, null, false, null, new Grafico(TipoGrafico.LINHA,
                        "Saldo devedor depois de cada prestação", Formato.MOEDA,
                        m.cronograma().stream().map(p -> String.valueOf(p.numero())).toList(),
                        List.of(new Serie("Saldo nominal", m.cronograma().stream().map(Parcela::saldoNominal).toList()),
                                new Serie("Saldo projetado", m.cronograma().stream().map(Parcela::saldoProjetado).toList())))));
            }
            secoes.add(secao("Memória de cálculo — " + nome, null, false, m.memoria(), colunas("Etapa", "Cálculo", "Valor", "Fundamento"),
                    e -> linha(e.descricao(), e.formula(), e.valor() == null ? "" : "R$".equals(e.unidade()) ? Formatos.moeda(e.valor())
                            : e.valor().stripTrailingZeros().toPlainString().replace('.', ',') + " " + Objects.toString(e.unidade(), ""),
                            Objects.toString(e.fundamento(), "")), null, m.fundamento()));
        }

        var in = s.insumos();
        secoes.add(new Secao("Capacidade de pagamento", "CAPAG oficial: " + Objects.toString(in.capagOficial(), "não importada")
                + (in.capagOficialData() == null ? "" : " (" + Formatos.data(in.capagOficialData()) + ")")
                + ". CAPAG estimada: " + Objects.toString(in.capagEstimada(), "não calculada")
                + " (estimativa do consultor; só entra como cenário \"" + MotorParcelamento.ROTULO_REVISAO + "\"). Parcela sustentável: "
                + (in.parcelaSustentavel() == null ? "não disponível" : Formatos.moeda(in.parcelaSustentavel()) + " (análise gerencial, não é critério oficial)")
                + ".", false, null, null));

        List<String> riscos = new ArrayList<>();
        for (ResultadoModalidade m : recomendadas) {
            String nome = m.nome() + ": ";
            if (m.rescisao() != null) riscos.add(nome + "rescisão — " + m.rescisao() + ".");
            if (m.garantia() != null) riscos.add(nome + "garantia — " + m.garantia() + ".");
            if (m.selicProjetada() != null) {
                riscos.add(nome + "parcelas futuras com SELIC projetada de " + percentual(m.selicProjetada()) + " a.m. (" + m.selicProjetadaOrigem()
                        + "); a SELIC real muda o custo.");
            }
            regras.modalidades(s.dataBase()).stream().filter(r -> r.chave().equals(m.chave()) && r.adesaoFim() != null)
                    .findFirst().ifPresent(r -> riscos.add(nome + "adesão até " + Formatos.data(r.adesaoFim()) + "; depois, a proposta perde o efeito."));
            m.avisos().forEach(a -> riscos.add(nome + a));
        }
        secoes.add(secao("Notas de risco", "Sem riscos específicos além das condições gerais.", true, riscos, colunas("Considere"),
                List::of, null, null));

        Map<String, FonteUsada> fontes = new LinkedHashMap<>();
        comparadas.forEach(m -> m.fontes().forEach(f -> fontes.putIfAbsent(f.chave(), f)));
        secoes.add(secao("Fontes normativas", null, false, List.copyOf(fontes.values()),
                colunas("Texto", "Endereço", "Baixado em:DATA", "SHA-256", "Fonte"),
                f -> linha(f.titulo(), f.url(), f.data().toString(), Objects.toString(f.sha256(), "—"),
                        f.secundaria() ? "secundária: " + f.observacao() : "oficial" + (f.observacao() == null ? "" : " (" + f.observacao() + ")")),
                null, "Regras da versão " + s.versaoRegras() + "; índices: " + s.versaoIndices() + "."));

        List<Indicador> sumario = new ArrayList<>(List.of(
                new Indicador("Data-base", s.dataBase().toString(), Formato.DATA, "simulação nº " + s.id() + ", versão " + s.versao()),
                new Indicador("Custo projetado da recomendação", cru(s.custoRecomendado()), Formato.MOEDA, "soma por órgão")));
        for (RecomendacaoOrgao r : s.recomendacao()) {
            sumario.add(new Indicador(r.orgao().name(), r.nome(), Formato.TEXTO, null));
        }
        List<String> notas = new ArrayList<>(in.avisos());
        notas.add("Simulação nº " + s.id() + " (versão " + s.versao() + "), regras " + s.versaoRegras() + ", índices " + s.versaoIndices() + ".");
        return new Corpo(sumario, secoes, documentos, notas);
    }

    private static List<String> linha(String... celulas) {
        return Arrays.asList(celulas);
    }

    private static String cru(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    private static String percentual(BigDecimal v) {
        return v == null ? "—" : v.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
    }
}
