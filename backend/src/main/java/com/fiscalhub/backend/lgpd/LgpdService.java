package com.fiscalhub.backend.lgpd;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.AuditoriaEvento;
import com.fiscalhub.backend.auditoria.AuditoriaEventoRepository;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.repository.ClienteRepository;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.AcessoRecursoService;
import com.fiscalhub.backend.storage.ArmazenamentoArquivos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSetMetaData;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Direitos do titular sobre os dados de um cliente (o escritório é o controlador; o FiscalHub, o operador):
 * exclusão com dupla confirmação e prazo de arrependimento e portabilidade em ZIP. As duas usam o mesmo
 * {@link GrafoCliente}: o que é exportado é exatamente o que é excluído.
 */
@Service
public class LgpdService {

    private static final Logger log = LoggerFactory.getLogger(LgpdService.class);

    private final AcessoRecursoService acesso;
    private final ClienteRepository clientes;
    private final GrafoCliente grafo;
    private final JdbcTemplate jdbc;
    private final ArmazenamentoArquivos armazenamento;
    private final AuditoriaEventoRepository auditoria;
    private final ObjectMapper json;
    private final TransactionTemplate transacao;
    private final TransactionTemplate leitura;
    private final int prazoDias;

    public LgpdService(AcessoRecursoService acesso, ClienteRepository clientes, GrafoCliente grafo, JdbcTemplate jdbc,
                       ArmazenamentoArquivos armazenamento, AuditoriaEventoRepository auditoria, ObjectMapper json,
                       PlatformTransactionManager tm, @Value("${fiscalhub.lgpd.prazo-exclusao-dias:7}") int prazoDias) {
        this.acesso = acesso;
        this.clientes = clientes;
        this.grafo = grafo;
        this.jdbc = jdbc;
        this.armazenamento = armazenamento;
        this.auditoria = auditoria;
        this.json = json;
        this.transacao = new TransactionTemplate(tm);
        this.leitura = new TransactionTemplate(tm);
        this.leitura.setReadOnly(true);
        this.prazoDias = prazoDias;
    }

    // ---- exclusão

    /** Segunda confirmação: o CNPJ do cliente digitado. A exclusão só acontece depois do prazo de arrependimento. */
    @Transactional
    public Cliente solicitarExclusao(long clienteId, String confirmacao, UsuarioAutenticado ua) {
        Cliente c = acesso.cliente(clienteId, ua);
        if (c.getExclusaoPrevistaPara() != null) {
            throw new BusinessRuleException("A exclusão deste cliente já está agendada.");
        }
        if (confirmacao == null || !digitos(confirmacao).equals(digitos(c.getCnpj()))) {
            throw new BusinessRuleException("Para confirmar, digite o CNPJ do cliente.");
        }
        LocalDateTime agora = LocalDateTime.now();
        c.setExclusaoSolicitadaEm(agora);
        c.setExclusaoSolicitadaPor(ua.id());
        c.setExclusaoPrevistaPara(agora.plusDays(prazoDias));
        return c;
    }

    @Transactional
    public Cliente cancelarExclusao(long clienteId, UsuarioAutenticado ua) {
        Cliente c = acesso.cliente(clienteId, ua);
        if (c.getExclusaoPrevistaPara() == null) {
            throw new BusinessRuleException("Não há exclusão agendada para este cliente.");
        }
        c.setExclusaoSolicitadaEm(null);
        c.setExclusaoSolicitadaPor(null);
        c.setExclusaoPrevistaPara(null);
        return c;
    }

    /** De hora em hora: conclui as exclusões cujo prazo de arrependimento venceu. */
    @Scheduled(cron = "${fiscalhub.lgpd.cron:0 15 * * * *}")
    public void concluirVencidas() {
        for (Long id : jdbc.queryForList("SELECT id FROM cliente WHERE exclusao_prevista_para <= now() ORDER BY id", Long.class)) {
            try {
                concluir(id);
            } catch (RuntimeException e) {
                log.error("Exclusão do cliente {} falhou; nova tentativa na próxima execução", id, e);
            }
        }
    }

    /**
     * Arquivos primeiro (todas as versões; apagar de novo não dá erro, então a falha no banco é retomada na próxima
     * execução), depois todas as linhas do cliente numa transação. A auditoria fica: guarda só ids, que deixam de
     * apontar para qualquer dado do cliente (pseudonimização), e ganha o registro da conclusão, sem nome nem CNPJ.
     */
    void concluir(long clienteId) {
        Map<String, Object> pedido = jdbc.queryForMap(
                "SELECT escritorio_id, exclusao_solicitada_por FROM cliente WHERE id = ? AND exclusao_prevista_para <= now()", clienteId);
        List<String> chaves = jdbc.queryForList("""
                SELECT chave_armazenamento FROM documento_fiscal WHERE cliente_id = ?
                UNION ALL SELECT chave_pdf FROM relatorio_emitido WHERE cliente_id = ?
                UNION ALL SELECT chave_xlsx FROM relatorio_emitido WHERE cliente_id = ?""", String.class, clienteId, clienteId, clienteId);
        chaves.forEach(armazenamento::excluirTodasVersoes);

        Map<String, Integer> linhas = new LinkedHashMap<>();
        transacao.executeWithoutResult(s -> {
            for (GrafoCliente.Tabela t : grafo.ordemExclusao()) {
                int n = jdbc.update("DELETE FROM \"" + t.nome() + "\" WHERE " + t.filtro(), clienteId);
                if (n > 0) {
                    linhas.put(t.nome(), n);
                }
            }
            AuditoriaEvento e = new AuditoriaEvento();
            e.setAcao(AcaoAuditoria.CLIENTE_EXCLUIR);
            e.setRecursoTipo(AcaoAuditoria.CLIENTE_EXCLUIR.recurso());
            e.setRecursoId(clienteId);
            e.setEscritorioId(((Number) pedido.get("escritorio_id")).longValue());
            e.setUsuarioId(pedido.get("exclusao_solicitada_por") == null ? null : ((Number) pedido.get("exclusao_solicitada_por")).longValue());
            e.setDetalhes(Map.of("resultado", "SUCESSO", "etapa", "CONCLUIDA", "arquivos", chaves.size(), "linhas", linhas));
            auditoria.save(e);
        });
        log.info("Exclusão do cliente {} concluída: {} arquivo(s), {}", clienteId, chaves.size(), linhas);
    }

    // ---- portabilidade

    /** Confere o acesso antes de começar a resposta (o ZIP é escrito depois, em outra thread). */
    public String nomeExportacao(long clienteId, UsuarioAutenticado ua) {
        Cliente c = acesso.cliente(clienteId, ua);
        return "fiscalhub-cliente-" + c.getId() + "-" + LocalDateTime.now().toLocalDate() + ".zip";
    }

    /**
     * ZIP com tudo do cliente: os arquivos originais, os relatórios emitidos (PDF e Excel gravados na emissão) e uma
     * tabela JSON por tabela do banco (linhas escritas em fluxo, sem montar a tabela na memória).
     */
    public void exportar(long clienteId, UsuarioAutenticado ua, OutputStream saida) {
        acesso.cliente(clienteId, ua);
        leitura.executeWithoutResult(s -> {
            try (ZipOutputStream zip = new ZipOutputStream(saida, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry("LEIAME.txt"));
                zip.write(LEIAME.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                for (GrafoCliente.Tabela t : grafo.tabelas()) {
                    zip.putNextEntry(new ZipEntry("dados/" + t.nome() + ".json"));
                    escreverTabela(t, clienteId, zip);
                    zip.closeEntry();
                }
                jdbc.query("SELECT id, nome_original_arquivo, chave_armazenamento FROM documento_fiscal WHERE cliente_id = ? ORDER BY id",
                        rs -> {
                            copiar(zip, "documentos/" + rs.getLong(1) + "-" + seguro(rs.getString(2)), rs.getString(3));
                        }, clienteId);
                jdbc.query("SELECT numero, tipo, chave_pdf, chave_xlsx FROM relatorio_emitido WHERE cliente_id = ? ORDER BY numero",
                        rs -> {
                            String base = "relatorios/" + rs.getLong(1) + "-" + rs.getString(2).toLowerCase();
                            copiar(zip, base + ".pdf", rs.getString(3));
                            copiar(zip, base + ".xlsx", rs.getString(4));
                        }, clienteId);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void escreverTabela(GrafoCliente.Tabela t, long clienteId, OutputStream zip) {
        try {
            JsonGenerator g = json.getFactory().createGenerator(new NaoFechar(zip));
            g.useDefaultPrettyPrinter();
            g.writeStartArray();
            jdbc.query(con -> {
                var ps = con.prepareStatement("SELECT * FROM \"" + t.nome() + "\" WHERE " + t.filtro() + " ORDER BY 1");
                ps.setFetchSize(1000);
                ps.setLong(1, clienteId);
                return ps;
            }, rs -> {
                ResultSetMetaData md = rs.getMetaData();
                try {
                    g.writeStartObject();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        g.writeFieldName(md.getColumnLabel(i));
                        json.writeValue(g, valor(rs.getObject(i)));
                    }
                    g.writeEndObject();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            g.writeEndArray();
            g.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void copiar(ZipOutputStream zip, String nome, String chave) {
        try (InputStream in = armazenamento.abrir(chave)) {
            zip.putNextEntry(new ZipEntry(nome));
            in.transferTo(zip);
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** jsonb (PGobject, do driver em runtime) vira o texto JSON; timestamp, ISO sem fuso (como no banco). */
    private static Object valor(Object v) {
        if (v != null && v.getClass().getName().equals("org.postgresql.util.PGobject")) {
            return v.toString();
        }
        return v instanceof java.sql.Timestamp ts ? ts.toLocalDateTime().toString() : v;
    }

    private static String seguro(String nome) {
        return nome == null ? "arquivo" : nome.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    }

    private static String digitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    /** O gerador do Jackson fecharia o ZIP inteiro ao terminar a tabela. */
    private static final class NaoFechar extends java.io.FilterOutputStream {
        NaoFechar(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }

    private static final String LEIAME = """
            Exportação dos dados de um cliente do FiscalHub (portabilidade, LGPD art. 18, V).

            documentos/   arquivos originais importados, como foram enviados (id-nome)
            relatorios/   relatórios emitidos: o PDF e o Excel gravados na emissão (numero-tipo)
            dados/        uma tabela JSON por tabela do banco com os dados do cliente e os derivados da importação
                          (escriturações, saldos, apurações, débitos, pagamentos, simulações, pontos de atenção...).
                          Os nomes das colunas são os do banco; valores em reais com ponto decimal; datas ISO 8601.

            Gerado pelo FiscalHub. O escritório é o controlador destes dados.
            """;
}
