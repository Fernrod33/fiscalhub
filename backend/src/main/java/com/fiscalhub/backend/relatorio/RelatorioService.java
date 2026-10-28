package com.fiscalhub.backend.relatorio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.dto.ArquivoDocumento;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.relatorio.MontagemRelatorios.Parametros;
import com.fiscalhub.backend.relatorio.ModeloRelatorio.TipoRelatorio;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.AcessoRecursoService;
import com.fiscalhub.backend.storage.ArmazenamentoArquivos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Prévia e emissão dos relatórios. A emissão grava o snapshot JSON do modelo e gera o PDF e o Excel A PARTIR DO
 * SNAPSHOT (lido de volta), guardando os dois no storage com SHA-256: baixar devolve sempre o mesmo arquivo. Reemitir
 * monta tudo de novo (os dados podem ter mudado) e ganha outro número. A tabela não aceita UPDATE (trigger).
 */
@Service
public class RelatorioService {

    public record RelatorioEmitido(long id, long clienteId, long numero, TipoRelatorio tipo, String titulo, String periodo,
                                   Parametros parametros, String emitidoPor, LocalDateTime emitidoEm, String sha256Snapshot,
                                   String sha256Pdf, String sha256Xlsx, String versaoTemplate, String versaoNormas) {
    }

    private final MontagemRelatorios montagem;
    private final RenderizadorRelatorio renderizador;
    private final ArmazenamentoArquivos armazenamento;
    private final AcessoRecursoService acesso;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public RelatorioService(MontagemRelatorios montagem, RenderizadorRelatorio renderizador, ArmazenamentoArquivos armazenamento,
                            AcessoRecursoService acesso, JdbcTemplate jdbc, ObjectMapper json) {
        this.montagem = montagem;
        this.renderizador = renderizador;
        this.armazenamento = armazenamento;
        this.acesso = acesso;
        this.jdbc = jdbc;
        this.json = json;
    }

    /** HTML da prévia, sem gravar nada (sem número nem hash). */
    @Transactional(readOnly = true)
    public String previa(long clienteId, Parametros p, UsuarioAutenticado ua) {
        return renderizador.html(montagem.montar(clienteId, p, agora(), ua), null, null);
    }

    @Transactional
    public RelatorioEmitido emitir(long clienteId, Parametros p, UsuarioAutenticado ua) {
        ModeloRelatorio montado = montagem.montar(clienteId, p, agora(), ua);
        // número sequencial por escritório: a trava na linha do escritório serializa as emissões dele
        jdbc.queryForObject("SELECT id FROM escritorio WHERE id = ? FOR UPDATE", Long.class, ua.escritorioId());
        long numero = jdbc.queryForObject("SELECT coalesce(max(numero), 0) + 1 FROM relatorio_emitido WHERE escritorio_id = ?",
                Long.class, ua.escritorioId());
        var c = montado.cabecalho();
        ModeloRelatorio comNumero = new ModeloRelatorio(montado.tipo(), montado.titulo(), new ModeloRelatorio.Cabecalho(c.escritorio(),
                c.logoChave(), c.cor(), c.rodape(), c.responsavel(), c.clienteRazaoSocial(), c.clienteCnpj(), c.periodo(), c.emissao(), numero),
                montado.sumario(), montado.secoes(), montado.notas(), montado.fontes(), montado.versoes());
        String snapshot = escrever(comNumero);
        String hash = sha256(snapshot.getBytes(StandardCharsets.UTF_8));
        ModeloRelatorio doSnapshot = ler(snapshot); // PDF e Excel saem do snapshot gravado, não do objeto em memória
        byte[] pdf = renderizador.pdf(doSnapshot, numero, hash);
        byte[] xlsx = renderizador.xlsx(doSnapshot, numero, hash);
        String base = "escritorios/" + ua.escritorioId() + "/relatorios/" + numero + "-" + UUID.randomUUID();
        armazenamento.salvar(base + ".pdf", new ByteArrayInputStream(pdf), pdf.length, "application/pdf");
        try {
            armazenamento.salvar(base + ".xlsx", new ByteArrayInputStream(xlsx), xlsx.length, XLSX);
            Long id = jdbc.queryForObject("""
                    INSERT INTO relatorio_emitido (escritorio_id, cliente_id, numero, tipo, parametros, snapshot, sha256_snapshot,
                        versao_template, versao_normas, chave_pdf, sha256_pdf, tamanho_pdf, chave_xlsx, sha256_xlsx, tamanho_xlsx,
                        emitido_por_id, emitido_em)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class,
                    ua.escritorioId(), clienteId, numero, p.tipo().name(), escrever(p), snapshot, hash, doSnapshot.versoes().template(),
                    doSnapshot.versoes().normas(), base + ".pdf", sha256(pdf), pdf.length, base + ".xlsx", sha256(xlsx), xlsx.length,
                    ua.id(), Timestamp.valueOf(c.emissao()));
            return consultar(clienteId, id, ua);
        } catch (RuntimeException e) {
            armazenamento.excluir(base + ".pdf"); // sem registro, os arquivos não ficam órfãos
            if (armazenamento.existe(base + ".xlsx")) {
                armazenamento.excluir(base + ".xlsx");
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<RelatorioEmitido> listar(long clienteId, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return jdbc.query(SELECT + " WHERE r.cliente_id = ? AND r.escritorio_id = ? ORDER BY r.numero DESC", mapper(), clienteId, ua.escritorioId());
    }

    @Transactional(readOnly = true)
    public RelatorioEmitido consultar(long clienteId, long id, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        return jdbc.query(SELECT + " WHERE r.id = ? AND r.cliente_id = ? AND r.escritorio_id = ?", mapper(), id, clienteId, ua.escritorioId())
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Relatório não encontrado com a ID: " + id));
    }

    /** O arquivo gravado na emissão (pdf ou xlsx), sem gerar de novo; uma consulta, já filtrada por cliente e escritório. */
    @Transactional(readOnly = true)
    public ArquivoDocumento arquivo(long clienteId, long id, String formato, UsuarioAutenticado ua) {
        acesso.cliente(clienteId, ua);
        boolean pdf = "pdf".equals(formato);
        return jdbc.query("SELECT numero, tipo, " + (pdf ? "chave_pdf, tamanho_pdf" : "chave_xlsx, tamanho_xlsx")
                        + " FROM relatorio_emitido WHERE id = ? AND cliente_id = ? AND escritorio_id = ?",
                (rs, i) -> new ArquivoDocumento(String.format("relatorio-%06d-%s.%s", rs.getLong(1),
                        rs.getString(2).toLowerCase().replace('_', '-'), formato), pdf ? "application/pdf" : XLSX, rs.getLong(4),
                        armazenamento.abrir(rs.getString(3))), id, clienteId, ua.escritorioId())
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Relatório não encontrado com a ID: " + id));
    }

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String SELECT = """
            SELECT r.id, r.cliente_id, r.numero, r.tipo, r.parametros::text, u.nome, r.emitido_em, r.sha256_snapshot, r.sha256_pdf,
                   r.sha256_xlsx, r.versao_template, r.versao_normas, r.snapshot::jsonb #>> '{cabecalho,periodo}'
            FROM relatorio_emitido r JOIN usuario u ON u.id = r.emitido_por_id""";

    private RowMapper<RelatorioEmitido> mapper() {
        return (rs, i) -> {
            TipoRelatorio tipo = TipoRelatorio.valueOf(rs.getString(4));
            return new RelatorioEmitido(rs.getLong(1), rs.getLong(2), rs.getLong(3), tipo, tipo.titulo, rs.getString(13),
                    lerParametros(rs.getString(5)), rs.getString(6), rs.getTimestamp(7).toLocalDateTime(), rs.getString(8), rs.getString(9),
                    rs.getString(10), rs.getString(11), rs.getString(12));
        };
    }

    /** Até o segundo: o snapshot e a tela mostram a mesma hora. */
    private static LocalDateTime agora() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private String escrever(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ModeloRelatorio ler(String snapshot) {
        try {
            return json.readValue(snapshot, ModeloRelatorio.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Parametros lerParametros(String texto) {
        try {
            return json.readValue(texto, Parametros.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
