package com.fiscalhub.backend.parcelamento;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.capacidade.CapacidadeService;
import com.fiscalhub.backend.capacidade.ParametrosCapacidade;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.parcelamento.MotorParcelamento.DebitoSimulado;
import com.fiscalhub.backend.parcelamento.MotorParcelamento.Pedido;
import com.fiscalhub.backend.parcelamento.MotorParcelamento.ResultadoModalidade;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.AcessoRecursoService;
import com.fiscalhub.backend.situacao.SituacaoFiscalService;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.CapacidadeOficial;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.DebitoConsolidado;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.OrigemDebito;
import com.fiscalhub.backend.situacao.SituacaoFiscalService.ParcelamentoFiscal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Simulações de parcelamento e transação de um cliente: monta os débitos e os insumos só do que foi importado (Situação
 * Fiscal, CAPAG oficial, capacidade), roda o {@link MotorParcelamento} e grava cada simulação com o resultado inteiro, a
 * versão das regras e dos índices e os parâmetros da projeção. Recalcular cria uma versão nova ligada à anterior.
 */
@Service
@Transactional(readOnly = true)
public class SimulacaoService {

    /** debitoIds: débitos da Situação Fiscal. dataBase: nula = hoje. parcelas: nula = o máximo de cada modalidade. */
    public record PedidoParcelamento(@NotEmpty List<Long> debitoIds, LocalDate dataBase, @Min(1) @Max(145) Integer parcelas,
                                  boolean reparcelamento) {
    }

    /** Insumos da simulação, todos importados ou parâmetro do escritório (com a origem). */
    public record InsumosSimulacao(String capagOficial, LocalDate capagOficialData, String capagEstimada,
                                   BigDecimal parcelaSustentavel, BigDecimal selicProjetadaParametro, boolean pessoaFisica,
                                   List<String> avisos) {
    }

    /** Melhor modalidade elegível de um órgão: a de menor custo entre as que cabem na parcela sustentável. */
    public record RecomendacaoOrgao(OrgaoCredor orgao, String chave, String versaoRegra, String cenario, String nome,
                                    BigDecimal custoProjetado, BigDecimal economiaVsAVista, Boolean cabe, String justificativa) {
    }

    public record SimulacaoCompleta(Long id, int versao, Long anteriorId, LocalDate dataBase, List<String> debitoChaves,
                                    PedidoParcelamento pedido, String versaoRegras, String versaoIndices, InsumosSimulacao insumos,
                                    List<ResultadoModalidade> modalidades, List<RecomendacaoOrgao> recomendacao,
                                    BigDecimal custoRecomendado, String motorAntigo, LocalDateTime criadoEm, String criadoPor) {
    }

    public record SimulacaoSalva(long id, int versao, int versaoMotor, Long anteriorId, LocalDate dataBase, LocalDateTime criadoEm,
                                 String criadoPor, String recomendacao, BigDecimal custoRecomendado, String versaoRegras,
                                 String versaoIndices, String motorAntigo) {
    }

    private final AcessoRecursoService acesso;
    private final SituacaoFiscalService situacao;
    private final CapacidadeService capacidade;
    private final ParametrosCapacidade parametros;
    private final MotorParcelamento motor;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public SimulacaoService(AcessoRecursoService acesso, SituacaoFiscalService situacao, CapacidadeService capacidade,
                            ParametrosCapacidade parametros, MotorParcelamento motor, JdbcTemplate jdbc, ObjectMapper json) {
        this.acesso = acesso;
        this.situacao = situacao;
        this.capacidade = capacidade;
        this.parametros = parametros;
        this.motor = motor;
        this.jdbc = jdbc;
        this.json = json;
    }

    public SimulacaoCompleta simular(long clienteId, PedidoParcelamento pedido, UsuarioAutenticado ua) {
        Cliente cliente = acesso.cliente(clienteId, ua);
        LocalDate dataBase = pedido.dataBase() != null ? pedido.dataBase() : LocalDate.now();
        Map<Long, DebitoConsolidado> todos = situacao.debitos(clienteId, null, null, ua).stream()
                .collect(Collectors.toMap(DebitoConsolidado::id, d -> d));
        if (!todos.keySet().containsAll(pedido.debitoIds())) {
            throw new ResourceNotFoundException("Débito não encontrado para o cliente informado.");
        }
        Map<String, LocalDate> inscricoes = new HashMap<>();
        Map<String, Integer> parcelamentosAnteriores = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (numero) numero, data_inscricao, parcelamentos FROM divida_detalhe
                WHERE cliente_id = ? ORDER BY numero, data_referencia DESC, id DESC""", rs -> {
            Date d = rs.getDate(2);
            inscricoes.put(rs.getString(1), d == null ? null : d.toLocalDate());
            parcelamentosAnteriores.put(rs.getString(1), (Integer) rs.getObject(3));
        }, clienteId);
        Map<String, ParcelamentoFiscal> parcelamentoDaInscricao = new HashMap<>();
        for (ParcelamentoFiscal p : situacao.parcelamentos(clienteId, ua)) {
            p.debitos().forEach(n -> parcelamentoDaInscricao.put(n.replaceFirst("^\\D+", ""), p)); // "CDA 2024..." → "2024..."
        }
        List<DebitoSimulado> debitos = new LinkedHashSet<>(pedido.debitoIds()).stream().map(todos::get)
                .map(d -> simulado(d, inscricoes, parcelamentosAnteriores, parcelamentoDaInscricao)).toList();

        List<String> avisos = new ArrayList<>();
        CapacidadeOficial capag = situacao.capacidades(clienteId, ua).stream().filter(c -> "PGFN".equals(c.fonte()))
                .max(Comparator.comparing(CapacidadeOficial::dataReferencia)).orElse(null);
        String estimada = null;
        BigDecimal sustentavel = null;
        try {
            CapacidadeService.ResumoCapacidade r = capacidade.resumo(clienteId, ua);
            estimada = r.capagEstimada();
            sustentavel = r.parcelaSustentavel();
        } catch (BusinessRuleException e) {
            avisos.add("Capacidade não calculada (" + e.getMessage() + "): sem cenário da CAPAG estimada e sem selo da parcela sustentável.");
        }
        if (sustentavel == null) {
            avisos.add("Parcela sustentável não disponível (análise gerencial): sem o selo \"cabe na parcela sustentável\".");
        }
        BigDecimal selic = parametros.consultar(ua.escritorioId()).gerencial().selicProjetada();
        boolean pf = cliente.getCnpj() != null && cliente.getCnpj().replaceAll("\\D", "").length() == 11;
        InsumosSimulacao insumos = new InsumosSimulacao(capag == null ? null : capag.classe(), capag == null ? null : capag.dataReferencia(),
                estimada, sustentavel, selic, pf, avisos);

        List<ResultadoModalidade> modalidades = motor.simular(debitos, new Pedido(dataBase, pedido.parcelas(), pf, insumos.capagOficial(),
                estimada, selic, sustentavel, pedido.reparcelamento()));
        List<RecomendacaoOrgao> recomendacao = recomendar(modalidades);
        BigDecimal custo = recomendacao.stream().map(RecomendacaoOrgao::custoProjetado).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SimulacaoCompleta(null, 1, null, dataBase, chaves(clienteId, pedido.debitoIds()),
                new PedidoParcelamento(pedido.debitoIds(), dataBase, pedido.parcelas(), pedido.reparcelamento()), motor.regras().versao(),
                versaoIndices(), insumos, modalidades, recomendacao, custo, null, null, null);
    }

    @Transactional
    public SimulacaoCompleta salvar(long clienteId, PedidoParcelamento pedido, UsuarioAutenticado ua) {
        return gravar(clienteId, simular(clienteId, pedido, ua), null, 1, ua);
    }

    public List<SimulacaoSalva> listar(long clienteId, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return jdbc.query("""
                SELECT s.id, s.versao, s.versao_motor, s.anterior_id, s.data_calculo, s.data_criacao, u.nome, s.recomendacao,
                       s.custo_recomendado, s.versao_regras, s.versao_indices, s.modalidade, s.valor_atualizado, s.numero_parcelas
                FROM simulacao_parcelamento s LEFT JOIN usuario u ON u.id = s.criado_por
                WHERE s.cliente_id = ? ORDER BY s.data_criacao DESC, s.id DESC""", (rs, i) -> new SimulacaoSalva(rs.getLong(1),
                rs.getInt(2), rs.getInt(3), (Long) rs.getObject(4), rs.getDate(5).toLocalDate(), rs.getTimestamp(6).toLocalDateTime(),
                rs.getString(7), rs.getString(8), rs.getBigDecimal(9), rs.getString(10), rs.getString(11),
                rs.getInt(3) == 1 ? motorAntigo(rs.getString(12), rs.getBigDecimal(13), (Integer) rs.getObject(14)) : null), clienteId);
    }

    public SimulacaoCompleta detalhe(long clienteId, long id, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        List<SimulacaoCompleta> s = jdbc.query("""
                SELECT s.resultado::text, s.versao, s.anterior_id, s.data_criacao, u.nome, s.versao_motor, s.modalidade,
                       s.valor_atualizado, s.numero_parcelas, s.data_calculo
                FROM simulacao_parcelamento s LEFT JOIN usuario u ON u.id = s.criado_por WHERE s.id = ? AND s.cliente_id = ?""",
                (rs, i) -> {
                    if (rs.getInt(6) == 1) {
                        return new SimulacaoCompleta(id, rs.getInt(2), null, rs.getDate(10).toLocalDate(), List.of(), null, null, null,
                                null, List.of(), List.of(), null, motorAntigo(rs.getString(7), rs.getBigDecimal(8), (Integer) rs.getObject(9)),
                                rs.getTimestamp(4).toLocalDateTime(), rs.getString(5));
                    }
                    SimulacaoCompleta c = ler(rs.getString(1));
                    return new SimulacaoCompleta(id, rs.getInt(2), (Long) rs.getObject(3), c.dataBase(), c.debitoChaves(), c.pedido(),
                            c.versaoRegras(), c.versaoIndices(), c.insumos(), c.modalidades(), c.recomendacao(), c.custoRecomendado(), null,
                            rs.getTimestamp(4).toLocalDateTime(), rs.getString(5));
                }, id, clienteId);
        return s.stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Simulação " + id + " não encontrada."));
    }

    /** Nova versão com as regras e os índices de hoje, mesma data-base e mesmos débitos (pela chave, estável entre reimportações). */
    @Transactional
    public SimulacaoCompleta recalcular(long clienteId, long id, UsuarioAutenticado ua) {
        SimulacaoCompleta antiga = detalhe(clienteId, id, ua);
        Map<Long, String> chaveDoId = new HashMap<>();
        jdbc.query("SELECT id, COALESCE(chave, 'ID|' || id) FROM debito WHERE cliente_id = ?",
                rs -> { chaveDoId.put(rs.getLong(1), rs.getString(2)); }, clienteId);
        Map<String, Long> idDaChave = situacao.debitos(clienteId, null, null, ua).stream()
                .collect(Collectors.toMap(d -> chaveDoId.get(d.id()), DebitoConsolidado::id, (a, b) -> a));
        List<String> chaves = antiga.motorAntigo() == null ? antiga.debitoChaves()
                : jdbc.queryForList("SELECT debito_id FROM simulacao_debito WHERE simulacao_id = ?", Long.class, id).stream()
                .map(chaveDoId::get).filter(Objects::nonNull).toList();
        List<Long> validos = chaves.stream().map(idDaChave::get).filter(Objects::nonNull).distinct().toList();
        if (validos.isEmpty()) {
            throw new BusinessRuleException("Os débitos desta simulação não estão mais na Situação Fiscal (documentos reimportados ou excluídos).");
        }
        PedidoParcelamento p = antiga.pedido() != null
                ? new PedidoParcelamento(validos, antiga.dataBase(), antiga.pedido().parcelas(), antiga.pedido().reparcelamento())
                : new PedidoParcelamento(validos, antiga.dataBase(), null, false);
        Integer versao = jdbc.queryForObject("""
                WITH RECURSIVE cadeia AS (SELECT id, anterior_id, versao FROM simulacao_parcelamento WHERE id = ?
                  UNION ALL SELECT s.id, s.anterior_id, s.versao FROM simulacao_parcelamento s JOIN cadeia c ON s.anterior_id = c.id)
                SELECT max(versao) FROM cadeia""", Integer.class, id);
        return gravar(clienteId, simular(clienteId, p, ua), id, versao + 1, ua);
    }

    @Transactional
    public void excluir(long clienteId, long id, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        if (jdbc.update("DELETE FROM simulacao_parcelamento WHERE id = ? AND cliente_id = ?", id, clienteId) == 0) {
            throw new ResourceNotFoundException("Simulação " + id + " não encontrada.");
        }
    }

    /** A simulação salva mais recente com recomendação (cartão da visão geral). */
    public SimulacaoSalva melhor(long clienteId, UsuarioAutenticado ua) {
        return listar(clienteId, ua).stream().filter(s -> s.recomendacao() != null).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private SimulacaoCompleta gravar(long clienteId, SimulacaoCompleta s, Long anterior, int versao, UsuarioAutenticado ua) {
        String texto = escrever(s);
        String recomendacao = s.recomendacao().isEmpty() ? null : s.recomendacao().stream()
                .map(r -> r.orgao() + ": " + r.nome()).collect(Collectors.joining("; "));
        LocalDateTime agora = LocalDateTime.now();
        Long id = jdbc.queryForObject("""
                INSERT INTO simulacao_parcelamento (cliente_id, data_calculo, aprovada, data_criacao, versao_motor, versao, anterior_id,
                    criado_por, parametros, resultado, versao_regras, versao_indices, recomendacao, custo_recomendado)
                VALUES (?, ?, FALSE, ?, 2, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?) RETURNING id""", Long.class,
                clienteId, Date.valueOf(s.dataBase()), Timestamp.valueOf(agora), versao, anterior, ua.id(),
                escrever(Map.of("pedido", s.pedido(), "debitoChaves", s.debitoChaves(), "selicProjetada",
                        Objects.requireNonNullElse(s.insumos().selicProjetadaParametro(), "média de 12 meses"))),
                texto, s.versaoRegras(), s.versaoIndices(), recomendacao, s.custoRecomendado());
        return new SimulacaoCompleta(id, versao, anterior, s.dataBase(), s.debitoChaves(), s.pedido(), s.versaoRegras(), s.versaoIndices(),
                s.insumos(), s.modalidades(), s.recomendacao(), s.custoRecomendado(), null, agora, null);
    }

    private DebitoSimulado simulado(DebitoConsolidado d, Map<String, LocalDate> inscricoes, Map<String, Integer> anteriores,
                                    Map<String, ParcelamentoFiscal> parcelamentos) {
        String numero = d.numeroInscricao();
        ParcelamentoFiscal p = numero == null ? null : parcelamentos.get(numero);
        String negociacao = d.negociacao() != null ? d.negociacao()
                : p != null ? p.modalidade() + " " + p.numero()
                : d.situacao() == com.fiscalhub.backend.entity.enums.SituacaoDebito.PARCELADO
                || d.situacao() == com.fiscalhub.backend.entity.enums.SituacaoDebito.NEGOCIADO ? d.situacaoDocumento() : null;
        LocalDate posicao = d.origens().stream().map(OrigemDebito::dataReferencia).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        String texto = Objects.toString(d.situacaoDocumento(), "") + " " + Objects.toString(d.referenciaOrigem(), "");
        String descricao = d.tributo() + (numero != null ? " " + numero : d.periodo() != null ? " " + d.periodo()
                : d.competencia() != null ? " " + YearMonth.from(d.competencia()) : "");
        Integer qtd = numero == null ? null : anteriores.get(numero);
        return new DebitoSimulado(d.id(), d.orgaoCredor(), d.tributo(), descricao, d.dataVencimento(), d.valorPrincipal(),
                d.valorMulta(), d.valorJuros(), d.valorEncargo(), d.valorTotal(), posicao, numero,
                numero == null ? null : inscricoes.get(numero), negociacao, p == null ? null : p.saldo(), qtd != null && qtd > 1,
                texto.contains("GIA-ST") || texto.contains("DeSTDA"), d.origens().isEmpty() ? null : d.origens().getFirst().origem());
    }

    static List<RecomendacaoOrgao> recomendar(List<ResultadoModalidade> modalidades) {
        List<RecomendacaoOrgao> out = new ArrayList<>();
        modalidades.stream().map(ResultadoModalidade::orgao).distinct().forEach(orgao -> {
            List<ResultadoModalidade> candidatas = modalidades.stream()
                    .filter(m -> m.orgao() == orgao && m.elegivel() && !"SE_REVISAO_DEFERIDA".equals(m.cenario())).toList();
            List<ResultadoModalidade> cabem = candidatas.stream().filter(m -> m.selo() == null || m.selo().cabe()).toList();
            boolean nenhumaCabe = cabem.isEmpty();
            (nenhumaCabe ? candidatas : cabem).stream().min(Comparator.comparing(ResultadoModalidade::custoProjetado)).ifPresent(m ->
                    out.add(new RecomendacaoOrgao(orgao, m.chave(), m.versaoRegra(), m.cenario(), m.nome() + (m.cenarioRotulo() == null ? "" : " (" + m.cenarioRotulo() + ")"),
                            m.custoProjetado(), m.economiaVsAVista(), m.selo() == null ? null : m.selo().cabe(),
                            nenhumaCabe ? "Menor custo; nenhuma opção cabe na parcela sustentável."
                                    : m.selo() == null ? "Menor custo entre as elegíveis (sem parcela sustentável para comparar)."
                                    : "Menor custo entre as que cabem na parcela sustentável.")));
        });
        return out;
    }

    private List<String> chaves(long clienteId, List<Long> ids) {
        return jdbc.queryForList("SELECT COALESCE(chave, 'ID|' || id) FROM debito WHERE cliente_id = ? AND id = ANY (?)", String.class,
                clienteId, ids.toArray(Long[]::new));
    }

    private String versaoIndices() {
        Integer selic = jdbc.queryForObject("SELECT max(ano * 100 + mes) FROM indice_monetario WHERE tipo = 'SELIC'", Integer.class);
        return "SELIC até " + (selic == null ? "—" : YearMonth.of(selic / 100, selic % 100)) + "; UPF/PR até "
                + motor.regras().ultimaUpf().map(YearMonth::toString).orElse("—");
    }

    private static String motorAntigo(String modalidade, BigDecimal valor, Integer parcelas) {
        return "Calculada pelo motor antigo (antes da Fase 5): " + modalidade + ", " + (parcelas == null ? "—" : parcelas) + " parcelas, total "
                + (valor == null ? "—" : valor.toPlainString()) + ". Recalcule para usar as regras e os índices vigentes.";
    }

    private SimulacaoCompleta ler(String texto) {
        try {
            return json.readValue(texto, SimulacaoCompleta.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String escrever(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
