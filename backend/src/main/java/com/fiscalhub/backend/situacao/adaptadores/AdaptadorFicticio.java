package com.fiscalhub.backend.situacao.adaptadores;

import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.pdf.AdaptadorDocumentoPdf;
import com.fiscalhub.backend.pdf.DocumentoPdf;
import com.fiscalhub.backend.pdf.DocumentoPdf.Registro;
import com.fiscalhub.backend.pdf.LeitorPdf;
import com.fiscalhub.backend.situacao.CodigosReceita;
import com.fiscalhub.backend.situacao.Extracao;
import com.fiscalhub.backend.situacao.Extracao.Origem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base dos adaptadores da versão ficticio-2026-10: os PDFs da massa fictícia, que imitam o conteúdo (não o visual) dos
 * sistemas. Reconhece só com a faixa da massa ("DOCUMENTO FICTÍCIO PARA TESTE DE SOFTWARE (FiscalHub)") e o título do
 * documento: PDF real não é reconhecido por estes adaptadores e cai em LAYOUT_NAO_RECONHECIDO, até ganhar o seu.
 */
abstract class AdaptadorFicticio implements AdaptadorDocumentoPdf {

    static final String VERSAO = "ficticio-2026-10";
    static final String FAIXA = "DOCUMENTO FICTÍCIO PARA TESTE DE SOFTWARE (FiscalHub)";
    private static final Pattern EMISSAO = Pattern.compile("Emitido em (\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern INSCRICAO = Pattern.compile("\\d{2} \\d \\d{2} \\d{6}-\\d{2}|[\\d.]+-\\d \\(DEBCAD\\)");
    private static final Map<String, TipoTributo> TRIBUTOS_POR_NOME = Map.of("IRPJ", TipoTributo.IRPJ, "CSLL", TipoTributo.CSLL,
            "PIS", TipoTributo.PIS, "COFINS", TipoTributo.COFINS, "IPI", TipoTributo.IPI, "ICMS-ST", TipoTributo.ICMS_ST,
            "ICMS", TipoTributo.ICMS, "Contribuições previdenciárias", TipoTributo.INSS);

    /** Título do documento, como aparece no PDF. */
    abstract String titulo();

    @Override
    public String versao() {
        return VERSAO;
    }

    @Override
    public boolean reconhece(DocumentoPdf pdf) {
        return pdf.contem(FAIXA, titulo());
    }

    /** Data de emissão do rodapé ("Emitido em dd/mm/aaaa"): a data de referência do relatório. */
    static LocalDate emissao(DocumentoPdf pdf) {
        Matcher m = EMISSAO.matcher(pdf.texto());
        if (!m.find()) {
            throw new NumberFormatException("Data de emissão não encontrada no rodapé.");
        }
        return LeitorPdf.data(m.group(1));
    }

    /** Empresa pelos campos "CNPJ" e "Nome empresarial" (identificação padrão dos relatórios). */
    static void empresa(DocumentoPdf pdf, Extracao e) {
        e.cnpj = pdf.campo("CNPJ").map(c -> LeitorPdf.cnpj(c.get(1))).orElse(null);
        e.nome = pdf.campo("Nome empresarial").map(c -> c.get(1)).orElse(null);
    }

    /** Empresa numa linha "Rótulo: 11.222.333/0001-81 - NOME" (extratos da PGFN, relatório de pagamentos). */
    static void empresaNaLinha(DocumentoPdf pdf, String rotulo, Extracao e) {
        pdf.linha(Pattern.compile("^" + Pattern.quote(rotulo))).ifPresent(l -> {
            e.cnpj = LeitorPdf.cnpj(l.texto());
            Matcher nome = Pattern.compile("\\d{2}-\\d{2} - (.+)$").matcher(l.texto());
            if (nome.find()) {
                e.nome = nome.group(1);
            }
        });
    }

    static Origem origem(Registro r) {
        return Origem.de(r.origem());
    }

    static BigDecimal valor(String texto) {
        return LeitorPdf.valor(texto);
    }

    static LocalDate data(String texto) {
        return LeitorPdf.data(texto);
    }

    /** "08/2026" -> competência; outro formato ("2019", "03/2022 a 06/2022", "12/36"): null. */
    static YearMonth mes(String texto) {
        return texto.matches("\\d{2}/\\d{4}") ? LeitorPdf.competencia(texto) : null;
    }

    static Pattern padraoInscricao() {
        return INSCRICAO;
    }

    /** Tributo pelo código de receita do texto ("5123-01 - IPI") ou, sem código, pelo nome ("IPI - Demais produtos"). */
    static Optional<TipoTributo> tributo(String texto) {
        Optional<TipoTributo> porCodigo = CodigosReceita.tributo(texto);
        if (porCodigo.isPresent()) {
            return porCodigo;
        }
        return TRIBUTOS_POR_NOME.entrySet().stream()
                .filter(t -> texto.startsWith(t.getKey()) || texto.contains("- " + t.getKey() + " "))
                .max((a, b) -> a.getKey().length() - b.getKey().length())
                .map(Map.Entry::getValue);
    }
}
