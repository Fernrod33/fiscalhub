package com.fiscalhub.backend.situacao;

import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.SituacaoDebito;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.AcessoRecursoService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Consulta da situação fiscal do cliente, juntando as fontes:
 * <ul>
 *   <li>relatórios que são retrato (situação fiscal, Regularize, CAPAG, conta corrente, RTI): só o mais recente de cada
 *   tipo vale (o débito que sumiu do relatório novo foi quitado ou mudou);</li>
 *   <li>a mesma dívida em tipos diferentes (situação fiscal e Regularize) é um débito só, com as origens de todos; os
 *   valores vêm do documento mais recente que os traz; valor diferente entre fontes marca o débito como divergente;</li>
 *   <li>declarações: as vigentes de cada período (a retificadora já substituiu a original); a mesma declaração em PDF e
 *   .dec é uma só;</li>
 *   <li>pagamentos: todos os documentos, o mesmo pagamento (mesma chave) uma vez só, com os itens do próprio documento de
 *   arrecadação (DARF, GR-PR) quando houver.</li>
 * </ul>
 * Isolamento por escritório pelo AcessoRecursoService.
 */
@Service
@Transactional(readOnly = true)
public class SituacaoFiscalService {

    /** De onde veio um valor: documento, página (PDF) ou registro (SPED/.dec), linha e trecho. */
    public record OrigemValor(long documentoId, String arquivo, String tipoDocumento, Integer pagina, String registro,
                              Long linha, String trecho) {
    }

    public record DebitoConsolidado(long id, long clienteId, OrgaoCredor orgaoCredor, TipoTributo tributo, String codigoReceita,
                         LocalDate competencia, String periodo, LocalDate dataVencimento, BigDecimal valorPrincipal,
                         BigDecimal valorMulta, BigDecimal valorJuros, BigDecimal valorEncargo, BigDecimal valorTotal,
                         SituacaoDebito situacao, String situacaoDocumento, String numeroInscricao, String numeroProcesso,
                         Boolean ajuizado, String negociacao, boolean impedeCertidao, boolean divergente,
                         long documentoOrigemId, String referenciaOrigem, List<OrigemDebito> origens) {
    }

    /** Uma fonte do débito, com os valores que ela traz. */
    public record OrigemDebito(OrigemValor origem, LocalDate dataReferencia, BigDecimal principal, BigDecimal multa,
                               BigDecimal juros, BigDecimal encargo, BigDecimal total, String situacao) {
    }

    public record ParcelamentoFiscal(long id, OrgaoCredor orgao, String numero, String modalidade, Integer parcelasPagas,
                               Integer parcelasTotal, BigDecimal valorParcela, BigDecimal saldo, String situacao,
                               List<String> debitos, LocalDate dataReferencia, OrigemValor origem) {
    }

    /**
     * impedeCertidao: a pendência impede a CND e a CPEND, com o fundamento. Na redação vigente da Portaria Conjunta
     * RFB/PGFN 1.751/2014, art. 4º, I (dada pela PC RFB/PGFN 20/2023), só a irregularidade de declaração que trate de
     * informações previdenciárias (DCTFWeb, GFIP, eSocial) impede; omissão de outras declarações (EFD-Contribuições,
     * ECF, DCTF) não está no texto e fica como pendência sem esse efeito.
     */
    public record PendenciaFiscal(long id, String tipo, String descricao, LocalDate periodo, String situacao,
                                  LocalDate dataReferencia, OrigemValor origem, boolean impedeCertidao, String fundamento) {
    }

    public static final String FUNDAMENTO_CERTIDAO = "Portaria Conjunta RFB/PGFN 1.751/2014, art. 4º, I (redação da PC RFB/PGFN 20/2023)";
    private static final java.util.regex.Pattern PREVIDENCIARIA =
            java.util.regex.Pattern.compile("DCTF ?WEB|GFIP|E-?SOCIAL|PREVIDENCI", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Omissão ou irregularidade de declaração previdenciária: impede a certidão (ver PendenciaFiscal). */
    static boolean impedeCertidao(String tipo, String descricao) {
        return tipo != null && tipo.startsWith("OMISSAO") && descricao != null && PREVIDENCIARIA.matcher(descricao).find();
    }

    /** classeConsistente: a classe bate com a regra do órgão (false gera alerta na importação). */
    public record CapacidadeOficial(long id, String fonte, String classe, BigDecimal capacidade, BigDecimal passivo,
                                    BigDecimal razao, Boolean classeConsistente, LocalDate dataReferencia, OrigemValor origem) {
    }

    /** declaracao: DCTF, DCTFWEB ou CONTA_CORRENTE_PR; finalidade: ORIGINAL ou SUBSTITUTA (retificadora). */
    public record DebitoDeclarado(long id, String declaracao, String finalidade, String codigoReceita, TipoTributo tributo,
                            LocalDate periodo, LocalDate vencimento, BigDecimal valor, BigDecimal valorPago,
                            BigDecimal valorCompensado, BigDecimal valorSuspenso, BigDecimal valorParcelado,
                            boolean divergente, List<OrigemValor> origens) {
    }

    /** PagamentoConsolidado (um item por código): os valores do documento de arrecadação e as origens de todas as fontes. */
    public record PagamentoConsolidado(long id, String chave, String documento, String numero, String codigoReceita, TipoTributo tributo,
                            LocalDate periodo, String referencia, LocalDate vencimento, LocalDate dataPagamento,
                            BigDecimal principal, BigDecimal multa, BigDecimal juros, BigDecimal total, String parcelamento,
                            boolean divergente, List<OrigemValor> origens) {
    }

    /** Documentos que são retrato da situação: vale só o mais recente de cada tipo, por cliente. */
    private static final String VIGENTES_RETRATO = """
            SELECT DISTINCT ON (origem) id FROM escrituracao_contabil
            WHERE cliente_id = ? AND situacao = 'VIGENTE'
              AND origem IN ('RELATORIO_SITUACAO_FISCAL', 'EXTRATO_REGULARIZE', 'CAPAG', 'CONTA_CORRENTE_PR', 'RTI')
            ORDER BY origem, data_fim DESC, id DESC""";

    private final JdbcTemplate jdbc;
    private final AcessoRecursoService acesso;

    public SituacaoFiscalService(JdbcTemplate jdbc, AcessoRecursoService acesso) {
        this.jdbc = jdbc;
        this.acesso = acesso;
    }

    // ---------------------------------------------------------------- débitos

    /** Débitos do cliente, um por dívida (fontes juntas); orgao e situacao opcionais. */
    public List<DebitoConsolidado> debitos(long clienteId, OrgaoCredor orgao, SituacaoDebito situacao, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return debitos(clienteId).stream()
                .filter(d -> orgao == null || d.orgaoCredor() == orgao)
                .filter(d -> situacao == null || d.situacao() == situacao)
                .toList();
    }

    /** Sem checagem de acesso: para quem já checou (conciliação, resumo). */
    List<DebitoConsolidado> debitos(long clienteId) {
        List<Map<String, Object>> linhas = jdbc.queryForList("""
                SELECT d.*, doc.nome_original_arquivo, doc.tipo AS tipo_documento FROM debito d
                JOIN documento_fiscal doc ON doc.id = d.documento_origem_id
                WHERE d.cliente_id = ? AND (d.escrituracao_id IS NULL OR d.escrituracao_id IN (""" + VIGENTES_RETRATO + "))",
                clienteId, clienteId);
        Map<String, List<Map<String, Object>>> porChave = linhas.stream().collect(Collectors.groupingBy(
                l -> l.get("chave") == null ? "ID|" + l.get("id") : (String) l.get("chave"), LinkedHashMap::new, Collectors.toList()));
        List<DebitoConsolidado> debitos = new ArrayList<>();
        for (List<Map<String, Object>> fontes : porChave.values()) {
            fontes.sort(Comparator.comparing((Map<String, Object> l) -> (Date) l.get("data_referencia"),
                    Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(l -> (Long) l.get("id")).reversed());
            Map<String, Object> recente = fontes.getFirst();
            Map<String, Object> comValores = fontes.stream().filter(l -> l.get("valor_principal") != null || l.get("valor_total") != null)
                    .findFirst().orElse(recente);
            SituacaoDebito situacao = SituacaoDebito.valueOf((String) recente.get("situacao"));
            debitos.add(new DebitoConsolidado((Long) comValores.get("id"), clienteId, OrgaoCredor.valueOf((String) recente.get("orgao_credor")),
                    TipoTributo.valueOf((String) recente.get("tributo")), primeiro(fontes, "codigo_receita"),
                    data(primeiro(fontes, "competencia")), primeiro(fontes, "periodo"), data(primeiro(fontes, "data_vencimento")),
                    (BigDecimal) comValores.get("valor_principal"), (BigDecimal) comValores.get("valor_multa"),
                    (BigDecimal) comValores.get("valor_juros"), (BigDecimal) comValores.get("valor_encargo"),
                    total(comValores), situacao, (String) recente.get("situacao_documento"), primeiro(fontes, "numero_inscricao"),
                    primeiro(fontes, "numero_processo"), primeiro(fontes, "ajuizado"), primeiro(fontes, "negociacao"),
                    situacao.impedeCertidao(), divergente(fontes), (Long) comValores.get("documento_origem_id"),
                    (String) comValores.get("referencia_origem"),
                    fontes.stream().map(l -> new OrigemDebito(origem(l, "documento_origem_id"), data(l.get("data_referencia")),
                            (BigDecimal) l.get("valor_principal"), (BigDecimal) l.get("valor_multa"), (BigDecimal) l.get("valor_juros"),
                            (BigDecimal) l.get("valor_encargo"), total(l), (String) l.get("situacao_documento"))).toList()));
        }
        debitos.sort(Comparator.comparing(DebitoConsolidado::orgaoCredor).thenComparing(DebitoConsolidado::competencia,
                Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(DebitoConsolidado::id));
        return debitos;
    }

    private static BigDecimal total(Map<String, Object> l) {
        if (l.get("valor_total") != null) {
            return (BigDecimal) l.get("valor_total");
        }
        List<BigDecimal> partes = Stream.of("valor_principal", "valor_multa", "valor_juros", "valor_encargo")
                .map(c -> (BigDecimal) l.get(c)).filter(Objects::nonNull).toList();
        return partes.isEmpty() ? null : partes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Duas fontes trazem o mesmo valor (principal, multa, juros, encargo ou total) diferente. */
    private static boolean divergente(List<Map<String, Object>> fontes) {
        for (String campo : List.of("valor_principal", "valor_multa", "valor_juros", "valor_encargo", "valor_total")) {
            if (fontes.stream().map(l -> (BigDecimal) l.get(campo)).filter(Objects::nonNull).distinct()
                    .map(BigDecimal::stripTrailingZeros).collect(Collectors.toSet()).size() > 1) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- parcelamentos, pendências, capacidade

    public List<ParcelamentoFiscal> parcelamentos(long clienteId, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return parcelamentos(clienteId);
    }

    List<ParcelamentoFiscal> parcelamentos(long clienteId) {
        List<ParcelamentoFiscal> todos = jdbc.query("""
                SELECT p.*, e.documento_id, doc.nome_original_arquivo, doc.tipo AS tipo_documento FROM parcelamento p
                JOIN escrituracao_contabil e ON e.id = p.escrituracao_id
                JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE p.cliente_id = ? AND p.escrituracao_id IN (""" + VIGENTES_RETRATO + ") ORDER BY p.data_referencia DESC, p.id DESC",
                (rs, i) -> new ParcelamentoFiscal(rs.getLong("id"), OrgaoCredor.valueOf(rs.getString("orgao")), rs.getString("numero"),
                        rs.getString("modalidade"), (Integer) rs.getObject("parcelas_pagas"), (Integer) rs.getObject("parcelas_total"),
                        rs.getBigDecimal("valor_parcela"), rs.getBigDecimal("saldo"), rs.getString("situacao"),
                        rs.getString("debitos") == null || rs.getString("debitos").isEmpty() ? List.of()
                                : List.of(rs.getString("debitos").split("; ")),
                        rs.getDate("data_referencia").toLocalDate(), origem(rs)), clienteId, clienteId);
        return unicos(todos, p -> p.orgao() + "|" + p.numero());
    }

    public List<PendenciaFiscal> pendencias(long clienteId, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return pendencias(clienteId);
    }

    List<PendenciaFiscal> pendencias(long clienteId) {
        return jdbc.query("""
                SELECT p.*, e.documento_id, doc.nome_original_arquivo, doc.tipo AS tipo_documento FROM pendencia_fiscal p
                JOIN escrituracao_contabil e ON e.id = p.escrituracao_id
                JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE p.cliente_id = ? AND p.escrituracao_id IN (""" + VIGENTES_RETRATO + ") ORDER BY p.periodo DESC NULLS LAST, p.id",
                (rs, i) -> {
                    boolean impede = impedeCertidao(rs.getString("tipo"), rs.getString("descricao"));
                    return new PendenciaFiscal(rs.getLong("id"), rs.getString("tipo"), rs.getString("descricao"),
                            data(rs.getDate("periodo")), rs.getString("situacao"), rs.getDate("data_referencia").toLocalDate(),
                            origem(rs), impede, impede ? FUNDAMENTO_CERTIDAO : null);
                }, clienteId, clienteId);
    }

    public List<CapacidadeOficial> capacidades(long clienteId, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return capacidades(clienteId);
    }

    List<CapacidadeOficial> capacidades(long clienteId) {
        return jdbc.query("""
                SELECT c.*, e.documento_id, doc.nome_original_arquivo, doc.tipo AS tipo_documento FROM capacidade_pagamento_oficial c
                JOIN escrituracao_contabil e ON e.id = c.escrituracao_id
                JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE c.cliente_id = ? AND c.escrituracao_id IN (""" + VIGENTES_RETRATO + ") ORDER BY c.data_referencia DESC",
                (rs, i) -> {
                    BigDecimal capacidade = rs.getBigDecimal("capacidade");
                    BigDecimal passivo = rs.getBigDecimal("passivo");
                    BigDecimal razao = capacidade == null || passivo == null || passivo.signum() <= 0 ? null
                            : capacidade.divide(passivo, 2, java.math.RoundingMode.HALF_UP);
                    return new CapacidadeOficial(rs.getLong("id"), rs.getString("fonte"), rs.getString("classe"), capacidade,
                            passivo, razao, (Boolean) rs.getObject("classe_consistente"), rs.getDate("data_referencia").toLocalDate(),
                            origem(rs));
                }, clienteId, clienteId);
    }

    // ---------------------------------------------------------------- declarado e pago

    public List<DebitoDeclarado> declaracoes(long clienteId, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return declaracoes(clienteId);
    }

    /**
     * Débitos declarados das declarações vigentes (DCTF, DCTFWeb) e da conta corrente do ICMS mais recente. A mesma
     * declaração em PDF e .dec: um item por código, com as duas origens (divergente se os valores diferem).
     */
    List<DebitoDeclarado> declaracoes(long clienteId) {
        List<Map<String, Object>> linhas = jdbc.queryForList("""
                SELECT d.*, e.origem AS declaracao, e.finalidade, e.documento_id, doc.nome_original_arquivo, doc.tipo AS tipo_documento
                FROM debito_declarado d
                JOIN escrituracao_contabil e ON e.id = d.escrituracao_id AND e.situacao = 'VIGENTE'
                JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE d.cliente_id = ? AND (e.origem IN ('DCTF', 'DCTFWEB') OR e.id IN (""" + VIGENTES_RETRATO + """
                ))
                ORDER BY d.periodo, e.id DESC, d.id""", clienteId, clienteId);
        Map<String, List<Map<String, Object>>> grupos = linhas.stream().collect(Collectors.groupingBy(
                l -> l.get("declaracao") + "|" + l.get("periodo") + "|" + l.get("codigo_receita"), LinkedHashMap::new,
                Collectors.toList()));
        return grupos.values().stream().map(fontes -> {
            Map<String, Object> l = fontes.getFirst();
            boolean divergente = fontes.stream().map(f -> ((BigDecimal) f.get("valor")).stripTrailingZeros()).distinct().count() > 1;
            return new DebitoDeclarado((Long) l.get("id"), (String) l.get("declaracao"), (String) l.get("finalidade"),
                    (String) l.get("codigo_receita"), tributo((String) l.get("tributo")), data(l.get("periodo")),
                    data(l.get("vencimento")), (BigDecimal) l.get("valor"), (BigDecimal) l.get("valor_pago"),
                    (BigDecimal) l.get("valor_compensado"), (BigDecimal) l.get("valor_suspenso"),
                    (BigDecimal) l.get("valor_parcelado"), divergente,
                    fontes.stream().map(f -> origem(f, "documento_id")).toList());
        }).toList();
    }

    public List<PagamentoConsolidado> pagamentos(long clienteId, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return pagamentos(clienteId);
    }

    /**
     * Pagamentos, cada um uma vez: as fontes com a mesma chave são o mesmo pagamento; os itens (códigos e valores) vêm do
     * próprio documento de arrecadação (DARF ou GR-PR individual) quando importado, senão do relatório mais recente.
     * divergente: as fontes somam totais diferentes.
     */
    List<PagamentoConsolidado> pagamentos(long clienteId) {
        List<Map<String, Object>> linhas = jdbc.queryForList("""
                SELECT p.*, e.origem, e.documento_id, doc.nome_original_arquivo, doc.tipo AS tipo_documento FROM pagamento p
                JOIN escrituracao_contabil e ON e.id = p.escrituracao_id AND e.situacao = 'VIGENTE'
                JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE p.cliente_id = ? ORDER BY p.data_pagamento, p.chave, p.id""", clienteId);
        Map<String, List<Map<String, Object>>> porChave = linhas.stream().collect(Collectors.groupingBy(
                l -> (String) l.get("chave"), LinkedHashMap::new, Collectors.toList()));
        List<PagamentoConsolidado> pagamentos = new ArrayList<>();
        for (List<Map<String, Object>> fontes : porChave.values()) {
            Map<Long, List<Map<String, Object>>> porEscrituracao = fontes.stream().collect(Collectors.groupingBy(
                    l -> (Long) l.get("escrituracao_id"), LinkedHashMap::new, Collectors.toList()));
            List<Map<String, Object>> escolhida = porEscrituracao.values().stream()
                    .max(Comparator.comparing((List<Map<String, Object>> f) -> Set.of("DARF", "GR_PR").contains(f.getFirst().get("origem")))
                            .thenComparing(f -> (Long) f.getFirst().get("escrituracao_id"))).orElseThrow();
            boolean divergente = porEscrituracao.values().stream().map(f -> f.stream().map(l -> (BigDecimal) l.get("total"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)).map(BigDecimal::stripTrailingZeros).distinct().count() > 1;
            List<OrigemValor> origens = fontes.stream().map(l -> origem(l, "documento_id")).toList();
            for (Map<String, Object> l : escolhida) {
                pagamentos.add(new PagamentoConsolidado((Long) l.get("id"), (String) l.get("chave"), (String) l.get("documento"),
                        (String) l.get("numero"), (String) l.get("codigo_receita"), tributo((String) l.get("tributo")),
                        data(l.get("periodo")), (String) l.get("referencia"), data(l.get("vencimento")),
                        data(l.get("data_pagamento")), (BigDecimal) l.get("principal"), (BigDecimal) l.get("multa"),
                        (BigDecimal) l.get("juros"), (BigDecimal) l.get("total"), (String) l.get("parcelamento"), divergente,
                        origens.stream().filter(o -> o.documentoId() != (Long) l.get("documento_id")
                                || Objects.equals(o.linha(), ((Integer) l.get("linha")).longValue())).toList()));
            }
        }
        return pagamentos;
    }

    // ---------------------------------------------------------------- apoio

    /** Linhas de várias fontes: a primeira de cada chave (a consulta já vem do mais recente para o mais antigo). */
    private static <T> List<T> unicos(List<T> linhas, Function<T, String> chave) {
        Map<String, T> unicos = new LinkedHashMap<>();
        linhas.forEach(l -> unicos.putIfAbsent(chave.apply(l), l));
        return List.copyOf(unicos.values());
    }

    @SuppressWarnings("unchecked")
    private static <T> T primeiro(List<Map<String, Object>> fontes, String campo) {
        return (T) fontes.stream().map(l -> l.get(campo)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static TipoTributo tributo(String nome) {
        return nome == null ? null : TipoTributo.valueOf(nome);
    }

    static LocalDate data(Object data) {
        return data == null ? null : ((Date) data).toLocalDate();
    }

    private static OrigemValor origem(Map<String, Object> l, String colunaDocumento) {
        Integer pagina = (Integer) l.get("pagina");
        Integer linha = (Integer) l.get("linha");
        return new OrigemValor((Long) l.get(colunaDocumento), (String) l.get("nome_original_arquivo"),
                (String) l.get("tipo_documento"), pagina, pagina == null && linha != null ? "registro" : null,
                linha == null ? null : linha.longValue(), (String) l.get("trecho"));
    }

    private static OrigemValor origem(ResultSet rs) throws SQLException {
        Integer pagina = (Integer) rs.getObject("pagina");
        return new OrigemValor(rs.getLong("documento_id"), rs.getString("nome_original_arquivo"), rs.getString("tipo_documento"),
                pagina, pagina == null ? "registro" : null, rs.getLong("linha"), rs.getString("trecho"));
    }
}
