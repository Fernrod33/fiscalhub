package com.fiscalhub.backend.situacao;

import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.EtapaProcessamento;
import com.fiscalhub.backend.importacao.AguardandoUsuario;
import com.fiscalhub.backend.importacao.ContextoProcessamento;
import com.fiscalhub.backend.importacao.FalhaImportacao;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import com.fiscalhub.backend.importacao.ProcessadorDocumento;
import com.fiscalhub.backend.pdf.AdaptadorDocumentoPdf;
import com.fiscalhub.backend.pdf.DocumentoPdf;
import com.fiscalhub.backend.pdf.LeitorPdf;
import com.fiscalhub.backend.situacao.adaptadores.LeitorDctfDec;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.DateTimeException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Documentos de débitos, declarado e pago: PDFs dos órgãos (situação fiscal, Regularize, CAPAG, DCTF, DCTFWeb, DARF,
 * pagamentos, conta corrente e RTI do PR, GR-PR) e o .dec da DCTF. O PDF passa pelo LeitorPdf e pelo único adaptador
 * que reconhece o layout; nenhum (ou mais de um) = LAYOUT_NAO_RECONHECIDO, nunca chute. A empresa vem do CNPJ do
 * documento; o que foi lido vai para o RepositorioSituacao.
 */
@Component
public class ProcessadorSituacao implements ProcessadorDocumento {

    private static final Set<DocumentoTipo> TIPOS = EnumSet.of(DocumentoTipo.PDF, DocumentoTipo.RELATORIO_SITUACAO_FISCAL,
            DocumentoTipo.EXTRATO_REGULARIZE, DocumentoTipo.CAPAG, DocumentoTipo.DCTF, DocumentoTipo.DCTFWEB,
            DocumentoTipo.DARF, DocumentoTipo.RELATORIO_PAGAMENTOS, DocumentoTipo.CONTA_CORRENTE_PR, DocumentoTipo.RTI,
            DocumentoTipo.GR_PR, DocumentoTipo.RELACAO_PAGAMENTOS_GR_PR);

    private final List<AdaptadorDocumentoPdf> adaptadores;
    private final RepositorioSituacao repositorio;

    public ProcessadorSituacao(List<AdaptadorDocumentoPdf> adaptadores, RepositorioSituacao repositorio) {
        this.adaptadores = adaptadores;
        this.repositorio = repositorio;
    }

    @Override
    public boolean suporta(DocumentoTipo tipo) {
        return TIPOS.contains(tipo);
    }

    @Override
    public void processar(DocumentoFiscal documento, ContextoProcessamento contexto) {
        contexto.etapa(EtapaProcessamento.LENDO);
        if (contexto.tamanho() > LeitorPdf.LIMITE_BYTES) {
            throw new FalhaImportacao("ARQUIVO_GRANDE_DEMAIS", "O arquivo tem " + contexto.tamanho() / 1024 / 1024
                    + " MB; o limite para PDF e .dec é " + LeitorPdf.LIMITE_BYTES / 1024 / 1024 + " MB.");
        }
        byte[] bytes = ler(contexto);
        contexto.lidos(bytes.length);
        Extracao extracao = new Extracao();
        String versao;
        if (documento.getTipo() == DocumentoTipo.DCTF) { // o detector só dá DCTF ao .dec; PDF chega como PDF
            versao = LeitorDctfDec.VERSAO;
            extrair(() -> LeitorDctfDec.ler(bytes, extracao), "DCTF (.dec)", versao);
        } else {
            DocumentoPdf pdf = LeitorPdf.ler(bytes);
            AdaptadorDocumentoPdf adaptador = adaptador(pdf, contexto);
            documento.setTipo(adaptador.tipo());
            versao = adaptador.versao();
            extrair(() -> adaptador.extrair(pdf, extracao), adaptador.tipo().name(), versao);
        }
        contexto.etapa(EtapaProcessamento.VALIDANDO);
        Cliente cliente;
        if (extracao.cnpj != null) {
            cliente = contexto.cliente(extracao.cnpj, extracao.nome);
        } else if (extracao.inscricaoEstadual != null) {
            cliente = contexto.clientePorInscricaoEstadual(extracao.inscricaoEstadual);
        } else {
            throw new FalhaImportacao("EMPRESA_AUSENTE", "O documento não traz o CNPJ nem a inscrição estadual da empresa: "
                    + "não dá para saber de qual cliente ele é.");
        }
        if (extracao.inscricaoEstadual != null && (cliente.getInscricaoEstadual() == null || cliente.getInscricaoEstadual().isBlank())) {
            cliente.setInscricaoEstadual(extracao.inscricaoEstadual); // o documento estadual completa o cadastro
        }
        contexto.etapa(EtapaProcessamento.GRAVANDO);
        repositorio.gravar(documento, cliente, versao, extracao, contexto);
    }

    /** Campo fora do formato num documento reconhecido: erro claro com o layout, não "falha inesperada". */
    private static void extrair(Runnable leitura, String tipo, String versao) {
        try {
            leitura.run();
        } catch (DateTimeException | IllegalArgumentException | IndexOutOfBoundsException e) { // NumberFormatException incluída
            throw new FalhaImportacao("CAMPO_ILEGIVEL", "O documento foi reconhecido como " + tipo + " (layout " + versao
                    + "), mas um campo não pôde ser lido: " + e.getMessage() + ". Confira se o arquivo é o documento original "
                    + "do sistema; se for, o layout mudou e precisa de um adaptador novo.");
        }
    }

    private AdaptadorDocumentoPdf adaptador(DocumentoPdf pdf, ContextoProcessamento contexto) {
        List<AdaptadorDocumentoPdf> reconhecem = adaptadores.stream().filter(a -> a.reconhece(pdf)).toList();
        if (reconhecem.size() == 1) {
            return reconhecem.getFirst();
        }
        String mensagem = reconhecem.isEmpty()
                ? "O layout deste PDF não é reconhecido. O arquivo fica guardado e será lido ao reprocessar quando houver "
                + "um adaptador para ele. Confira se é um dos documentos suportados (situação fiscal, Regularize, CAPAG, "
                + "DCTF, DCTFWeb, DARF, pagamentos, conta corrente e RTI do PR, GR-PR) gerado pelo próprio sistema."
                : "Mais de um layout reconhece este PDF (" + String.join(", ", reconhecem.stream()
                .map(a -> a.tipo() + " " + a.versao()).toList()) + "). Nenhum é usado: reporte o caso ao suporte.";
        contexto.registrar(Severidade.ALERTA, null, null, "LAYOUT_NAO_RECONHECIDO", mensagem);
        throw new AguardandoUsuario(DocumentoStatus.LAYOUT_NAO_RECONHECIDO, mensagem, null, null);
    }

    private static byte[] ler(ContextoProcessamento contexto) {
        try (InputStream in = contexto.abrir()) {
            return in.readNBytes((int) LeitorPdf.LIMITE_BYTES + 1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
