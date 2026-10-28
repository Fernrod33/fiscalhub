package com.fiscalhub.backend.controller;

import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.Auditavel;
import com.fiscalhub.backend.dto.ArquivoDocumento;
import com.fiscalhub.backend.relatorio.CargaTributariaService;
import com.fiscalhub.backend.relatorio.ModeloRelatorio.TipoRelatorio;
import com.fiscalhub.backend.relatorio.MontagemRelatorios.Parametros;
import com.fiscalhub.backend.relatorio.RelatorioService;
import com.fiscalhub.backend.relatorio.RelatorioService.RelatorioEmitido;
import com.fiscalhub.backend.security.ConsultorOuAdmin;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;

/**
 * Relatórios do cliente: prévia (HTML, nada gravado), emissão imutável numerada, histórico e download do PDF e do
 * Excel gravados na emissão. Emitir: ADMIN ou CONSULTOR; ver e baixar: todos do escritório. Cliente de outro = 404.
 */
@RestController
@RequestMapping("/api/clientes/{clienteId}")
public class RelatorioController {

    /** de/ate: competências (aaaa-mm); na carga tributária, valem os anos. Opções ausentes = true. */
    public record PedidoRelatorio(@NotNull TipoRelatorio tipo, YearMonth de, YearMonth ate, Boolean incluirDivergencias,
                                  Boolean incluirFontes) {
        Parametros parametros() {
            return new Parametros(tipo, de, ate, incluirDivergencias == null || incluirDivergencias, incluirFontes == null || incluirFontes);
        }
    }

    /** A prévia vai num iframe com sandbox; a política abaixo vale se o HTML for aberto direto. */
    private static final String CSP_PREVIA = "default-src 'none'; img-src data:; style-src 'unsafe-inline'; font-src data:";

    private final RelatorioService relatorios;
    private final CargaTributariaService carga;

    public RelatorioController(RelatorioService relatorios, CargaTributariaService carga) {
        this.relatorios = relatorios;
        this.carga = carga;
    }

    /** Prévia: só leitura, todos os papéis (emitir é que exige ADMIN ou CONSULTOR). */
    @PostMapping(value = "/relatorios/previa", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> previa(@PathVariable long clienteId, @Valid @RequestBody PedidoRelatorio pedido,
                                         @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.ok().header("Content-Security-Policy", CSP_PREVIA)
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(relatorios.previa(clienteId, pedido.parametros(), ua));
    }

    @PostMapping("/relatorios")
    @ConsultorOuAdmin
    @Auditavel(AcaoAuditoria.RELATORIO_EMITIR)
    public ResponseEntity<RelatorioEmitido> emitir(@PathVariable long clienteId, @Valid @RequestBody PedidoRelatorio pedido,
                                                   @AuthenticationPrincipal UsuarioAutenticado ua) {
        return ResponseEntity.status(HttpStatus.CREATED).body(relatorios.emitir(clienteId, pedido.parametros(), ua));
    }

    @GetMapping("/relatorios")
    public List<RelatorioEmitido> listar(@PathVariable long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return relatorios.listar(clienteId, ua);
    }

    @GetMapping("/relatorios/{relatorioId}")
    public RelatorioEmitido consultar(@PathVariable long clienteId, @PathVariable long relatorioId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return relatorios.consultar(clienteId, relatorioId, ua);
    }

    @GetMapping("/relatorios/{relatorioId}/pdf")
    @Auditavel(value = AcaoAuditoria.RELATORIO_BAIXAR, idParam = "relatorioId")
    public ResponseEntity<StreamingResponseBody> pdf(@PathVariable long clienteId, @PathVariable Long relatorioId,
                                                     @AuthenticationPrincipal UsuarioAutenticado ua) {
        return baixar(relatorios.arquivo(clienteId, relatorioId, "pdf", ua));
    }

    @GetMapping("/relatorios/{relatorioId}/xlsx")
    @Auditavel(value = AcaoAuditoria.RELATORIO_BAIXAR, idParam = "relatorioId")
    public ResponseEntity<StreamingResponseBody> xlsx(@PathVariable long clienteId, @PathVariable Long relatorioId,
                                                      @AuthenticationPrincipal UsuarioAutenticado ua) {
        return baixar(relatorios.arquivo(clienteId, relatorioId, "xlsx", ua));
    }

    /** Carga tributária efetiva por exercício (gráfico da visão geral; o relatório usa o mesmo serviço). */
    @GetMapping("/carga-tributaria")
    public List<CargaTributariaService.ExercicioCarga> carga(@PathVariable long clienteId, @AuthenticationPrincipal UsuarioAutenticado ua) {
        return carga.carga(clienteId, null, null, ua);
    }

    private static ResponseEntity<StreamingResponseBody> baixar(ArquivoDocumento arquivo) {
        StreamingResponseBody corpo = saida -> {
            try (InputStream in = arquivo.conteudo()) {
                in.transferTo(saida);
            }
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(arquivo.mimeType())).contentLength(arquivo.tamanho())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(arquivo.nome(), StandardCharsets.UTF_8).build().toString())
                .body(corpo);
    }
}
