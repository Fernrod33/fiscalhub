package com.fiscalhub.backend.ferramentas;

import com.fiscalhub.backend.util.CnpjValidationUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gera, a partir de um SPED (.txt) ou PDF real, uma versão com a mesma estrutura e os dados trocados, para virar fixture
 * de um adaptador novo sem expor o cliente. Uso (na pasta backend):
 * <pre>mvn -q compile exec:java -Dexec.mainClass=com.fiscalhub.backend.ferramentas.Anonimizador \
 *     -Dexec.args="--saida ../anonimizados [--escala 0.37] [--nomes nomes.txt] arquivo1.txt arquivo2.pdf ..."</pre>
 * <ul>
 *   <li>CNPJ e CPF (com ou sem máscara, só os de dígito verificador válido) viram fictícios válidos e consistentes: o
 *   mesmo original vira sempre o mesmo, em todos os arquivos da execução, e as filiais mantêm a raiz comum;</li>
 *   <li>SPED: nas linhas de identificação (as que têm CNPJ/CPF: 0000, 0100, 0150, J930, 0930, Y600...) os nomes,
 *   endereços, e-mails e telefones são trocados; contas, códigos e valores ficam. O número de linhas e de campos não muda
 *   (9900/9999 continuam valendo);</li>
 *   <li>PDF: o texto é reescrito no próprio conteúdo, com os glifos da fonte do documento (mesmo comprimento, mesmo
 *   lugar); os nomes achados nos SPED da mesma execução e os da lista (--nomes, um por linha) são trocados; metadados
 *   (autor, título) saem;</li>
 *   <li>valores ficam iguais; --escala multiplica os valores em reais (1.234,56) por um fator — quebra as somas e as
 *   validações do SPED: use para PDF.</li>
 * </ul>
 * Revise o resultado antes de enviar: nome que não está em linha de identificação nem na lista continua igual.
 */
public final class Anonimizador {

    private static final Pattern CNPJ = Pattern.compile("(?<!\\d)(\\d{2})\\.?(\\d{3})\\.?(\\d{3})/?(\\d{4})-?(\\d{2})(?!\\d)");
    private static final Pattern CPF = Pattern.compile("(?<!\\d)(\\d{3})\\.?(\\d{3})\\.?(\\d{3})-?(\\d{2})(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern VALOR = Pattern.compile("(?<![\\d,.])-?(\\d{1,3}(\\.\\d{3})+|\\d+),\\d{2}(?![\\d,])");
    private static final Pattern NOME = Pattern.compile("[\\p{L}][\\p{L}.&'\\- ]*\\p{L}");
    private static final Pattern TELEFONE = Pattern.compile("\\d{10,11}");
    /**
     * Registros de identificação de pessoas (Guias e Manuais do SPED): neles, campo com várias palavras é nome ou
     * endereço e campo de 10 ou 11 dígitos é telefone. 0000 (empresa), 0005/0030 (dados complementares e endereço),
     * 0100 (contabilista), 0150 (participante), 0930/J930/J932 (signatários), Y600/Y611/Y612 (sócios e dirigentes).
     */
    private static final java.util.Set<String> IDENTIFICACAO = java.util.Set.of("0000", "0005", "0030", "0100", "0150", "0930",
            "J930", "J932", "J935", "Y600", "Y611", "Y612");
    private static final String[] PALAVRAS = {"ALFA", "BETA", "GAMA", "DELTA", "OMEGA", "SIGMA", "NORTE", "SUL", "PRIMA", "NOVA",
            "SERRA", "VALE", "LAGO", "PORTO", "CAMPO", "MONTE", "RIO", "SOL", "LUA", "MAR"};

    private final Random aleatorio;
    private final BigDecimal escala;
    private final Map<String, String> raizes = new LinkedHashMap<>();
    private final Map<String, String> cpfs = new LinkedHashMap<>();
    /** Nomes reais achados (ou informados) → fictícios, do maior para o menor na hora de trocar. */
    final Map<String, String> nomes = new LinkedHashMap<>();
    final Map<String, Integer> contagem = new LinkedHashMap<>();

    public Anonimizador(long semente, BigDecimal escala) {
        this.aleatorio = new Random(semente);
        this.escala = escala;
    }

    public static void main(String[] args) throws IOException {
        Path saida = null;
        BigDecimal escala = null;
        List<Path> entradas = new ArrayList<>();
        List<String> listaNomes = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--saida" -> saida = Path.of(args[++i]);
                case "--escala" -> escala = new BigDecimal(args[++i]);
                case "--nomes" -> listaNomes.addAll(Files.readAllLines(Path.of(args[++i]), StandardCharsets.UTF_8));
                default -> entradas.add(Path.of(args[i]));
            }
        }
        if (saida == null || entradas.isEmpty()) {
            System.err.println("uso: Anonimizador --saida <pasta> [--escala fator] [--nomes arquivo] <arquivos .txt/.pdf>");
            System.exit(2);
        }
        Files.createDirectories(saida);
        Anonimizador a = new Anonimizador(new java.security.SecureRandom().nextLong(), escala);
        listaNomes.stream().map(String::trim).filter(n -> n.length() > 2).forEach(a::registrarNome);
        // SPED primeiro: os nomes achados neles valem para os PDF da mesma execução
        entradas.sort(Comparator.comparing(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".pdf")));
        for (Path e : entradas) {
            byte[] bytes = Files.readAllBytes(e);
            byte[] resultado = e.toString().toLowerCase(Locale.ROOT).endsWith(".pdf") ? a.pdf(bytes) : a.sped(bytes);
            Files.write(saida.resolve(a.texto(e.getFileName().toString())), resultado);
            System.out.println("ok: " + e.getFileName());
        }
        System.out.println("trocados: " + a.contagem + (escala == null ? "" : "; valores × " + escala));
        System.out.println("Revise os arquivos antes de enviar: nomes fora das linhas de identificação e da lista ficam como estão.");
    }

    // ---- SPED

    public byte[] sped(byte[] arquivo) {
        String conteudo = new String(arquivo, StandardCharsets.ISO_8859_1);
        StringBuilder saida = new StringBuilder();
        Matcher linhas = Pattern.compile("[^\\r\\n]*(\\r\\n|\\n|\\r|$)").matcher(conteudo);
        while (linhas.find() && linhas.end() > linhas.start()) {
            String linha = linhas.group();
            String fim = linhas.group(1);
            saida.append(linhaSped(linha.substring(0, linha.length() - fim.length()))).append(fim);
        }
        return saida.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private String linhaSped(String linha) {
        if (!linha.startsWith("|")) {
            return linha;
        }
        String[] campos = linha.split("\\|", -1);
        boolean identificacao = campos.length > 2 && IDENTIFICACAO.contains(campos[1]);
        for (int i = 2; i < campos.length - 1; i++) { // 0 vazio, 1 = REG, último vazio
            String c = campos[i];
            if (identificacao && TELEFONE.matcher(c).matches() && !documento(c)) {
                // telefone ou inscrição estadual: o mesmo número vira o mesmo fictício também nos PDFs da execução
                campos[i] = nomes.computeIfAbsent(c, k -> digitos(k.length()));
                conta("telefone ou inscrição");
            } else if (identificacao && c.length() > 3 && NOME.matcher(c).matches() && c.contains(" ")) {
                campos[i] = registrarNome(c);
            } else if (escala != null && VALOR.matcher(c).matches()) {
                campos[i] = escalar(c);
            } else {
                campos[i] = texto(c); // CNPJ, CPF, e-mail e os nomes já achados (ex.: o nome da empresa no I030 e no J900)
            }
        }
        return String.join("|", campos);
    }

    private static boolean documento(String c) {
        String d = c.replaceAll("[.\\-/]", "");
        return d.matches("\\d{14}") && CnpjValidationUtils.isValid(d) || d.matches("\\d{11}") && CnpjValidationUtils.isValidCpf(d);
    }

    // ---- texto (vale para SPED, PDF e nomes de arquivo)

    /** CNPJ, CPF, e-mails, nomes conhecidos e (com --escala) valores, mantendo o comprimento e a máscara. */
    public String texto(String s) {
        String r = substituir(CNPJ, s, m -> {
            String original = m.group(1) + m.group(2) + m.group(3) + m.group(4) + m.group(5);
            if (!CnpjValidationUtils.isValid(original)) {
                return m.group();
            }
            String raiz = raizes.computeIfAbsent(m.group(1) + m.group(2) + m.group(3), k -> digitos(8));
            String novo = comDv(raiz + m.group(4));
            conta("cnpj");
            return mascara(m.group(), novo);
        });
        r = substituir(CPF, r, m -> {
            String original = m.group(1) + m.group(2) + m.group(3) + m.group(4);
            if (!CnpjValidationUtils.isValidCpf(original)) {
                return m.group();
            }
            conta("cpf");
            return mascara(m.group(), cpfs.computeIfAbsent(original, k -> comDv(digitos(9))));
        });
        r = substituir(EMAIL, r, m -> email());
        for (var n : nomes.entrySet().stream().sorted((a, b) -> b.getKey().length() - a.getKey().length()).toList()) {
            if (r.contains(n.getKey())) {
                r = r.replace(n.getKey(), n.getValue());
                conta("nome");
            }
        }
        if (escala != null) {
            r = substituir(VALOR, r, m -> escalar(m.group()));
        }
        return r;
    }

    /** Nome fictício do mesmo comprimento (as palavras completadas com letras), lembrado para as próximas ocorrências. */
    String registrarNome(String original) {
        return nomes.computeIfAbsent(original, k -> {
            conta("nome");
            StringBuilder b = new StringBuilder();
            while (b.length() < k.length()) {
                b.append(b.isEmpty() ? "" : " ").append(PALAVRAS[aleatorio.nextInt(PALAVRAS.length)]);
            }
            String fict = b.substring(0, k.length()).stripTrailing();
            fict = fict + "X".repeat(k.length() - fict.length());
            return k.equals(k.toUpperCase(Locale.ROOT)) ? fict : fict.charAt(0) + fict.substring(1).toLowerCase(Locale.ROOT);
        });
    }

    private String escalar(String valor) {
        conta("valor");
        BigDecimal v = new BigDecimal(valor.replace(".", "").replace(',', '.')).multiply(escala).setScale(2, RoundingMode.HALF_UP);
        String novo = valor.contains(".") ? new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR"))).format(v)
                : v.toPlainString().replace('.', ',');
        return novo;
    }

    private String email() {
        conta("email");
        return "pessoa" + (aleatorio.nextInt(9000) + 1000) + "@exemplo.com.br";
    }

    private String digitos(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            b.append(aleatorio.nextInt(10));
        }
        return b.toString();
    }

    /** Acrescenta os dois dígitos verificadores (12 → CNPJ, 9 → CPF). */
    static String comDv(String base) {
        String s = base;
        for (int k = 0; k < 2; k++) {
            int soma = 0;
            for (int i = 0; i < s.length(); i++) {
                int peso = base.length() == 9 ? s.length() + 1 - i : (s.length() + 1 - i - 2) % 8 + 2;
                soma += (s.charAt(i) - '0') * peso;
            }
            int dv = soma % 11 < 2 ? 0 : 11 - soma % 11;
            s += dv;
        }
        return s;
    }

    private static String mascara(String modelo, String digitos) {
        StringBuilder b = new StringBuilder();
        int j = 0;
        for (char c : modelo.toCharArray()) {
            b.append(Character.isDigit(c) ? digitos.charAt(j++) : c);
        }
        return b.toString();
    }

    private static String substituir(Pattern p, String s, java.util.function.Function<Matcher, String> f) {
        Matcher m = p.matcher(s);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(b, Matcher.quoteReplacement(f.apply(m)));
        }
        return m.appendTail(b).toString();
    }

    private void conta(String tipo) {
        contagem.merge(tipo, 1, Integer::sum);
    }

    // ---- PDF

    /** Reescreve as strings dos operadores de texto (Tj, TJ, ', ") de cada página com a mesma fonte; tira os metadados. */
    public byte[] pdf(byte[] arquivo) throws IOException {
        try (PDDocument doc = Loader.loadPDF(arquivo)) {
            doc.setDocumentInformation(new PDDocumentInformation());
            doc.getDocumentCatalog().setMetadata(null);
            for (PDPage pagina : doc.getPages()) {
                PDFStreamParser parser = new PDFStreamParser(pagina);
                List<Object> tokens = parser.parse();
                PDFont fonte = null;
                for (int i = 0; i < tokens.size(); i++) {
                    if (!(tokens.get(i) instanceof Operator op)) {
                        continue;
                    }
                    switch (op.getName()) {
                        case "Tf" -> fonte = pagina.getResources().getFont((COSName) tokens.get(i - 2));
                        case "Tj", "'", "\"" -> {
                            if (fonte != null && tokens.get(i - 1) instanceof COSString s) {
                                tokens.set(i - 1, trocar(s, fonte));
                            }
                        }
                        case "TJ" -> {
                            if (fonte != null && tokens.get(i - 1) instanceof COSArray partes) {
                                for (int j = 0; j < partes.size(); j++) {
                                    if (partes.get(j) instanceof COSString s) {
                                        partes.set(j, trocar(s, fonte));
                                    }
                                }
                            }
                        }
                        default -> {
                        }
                    }
                }
                PDStream novo = new PDStream(doc);
                try (OutputStream out = novo.createOutputStream(COSName.FLATE_DECODE)) {
                    new ContentStreamWriter(out).writeTokens(tokens);
                }
                pagina.setContents(novo);
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
        }
    }

    /** Só troca se a fonte tem os glifos do texto novo (fonte embutida em subconjunto): senão deixa e avisa na contagem. */
    private COSString trocar(COSString s, PDFont fonte) throws IOException {
        StringBuilder texto = new StringBuilder();
        ByteArrayInputStream in = new ByteArrayInputStream(s.getBytes());
        while (in.available() > 0) {
            String u = fonte.toUnicode(fonte.readCode(in));
            texto.append(u == null ? "�" : u);
        }
        String original = texto.toString();
        String novo = texto(original);
        if (novo.equals(original)) {
            return s;
        }
        try {
            return new COSString(fonte.encode(novo));
        } catch (IllegalArgumentException | IOException semGlifo) {
            conta("pdf-sem-glifo"); // revise: este trecho ficou com o texto original
            return s;
        }
    }
}
