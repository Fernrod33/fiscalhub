package com.fiscalhub.backend.pdf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Texto de um PDF lido pelo {@link LeitorPdf}: páginas, linhas (de cima para baixo) e células (pela posição horizontal).
 * Traz a navegação comum aos adaptadores: seção pelo título, tabela e campo "rótulo | valor".
 * <p>
 * Uma linha de tabela com célula quebrada ocupa várias linhas de texto, e as células de uma linha só ficam centralizadas
 * na vertical (o primeiro pedaço da célula quebrada aparece acima da chave). Por isso o registro é um <b>bloco</b>:
 * linhas de texto seguidas a até {@link #VAO_BLOCO} vezes o tamanho da fonte. Toda linha sabe a própria origem.
 */
public record DocumentoPdf(List<Pagina> paginas) {

    /** Vão máximo entre linhas do mesmo bloco, em múltiplos da fonte (quebra de linha na célula ~1,2; entre linhas ~1,75). */
    static final float VAO_BLOCO = 1.45f;
    private static final Pattern PALAVRA_CORTADA_FIM = Pattern.compile("^\\S*[\\d./-]$");
    private static final Pattern PALAVRA_CORTADA_INICIO = Pattern.compile("^[\\d-].*");

    public record Pagina(int numero, List<Linha> linhas) {
    }

    public record Celula(float x, String texto) {
    }

    /** De onde veio um valor: página e linha do PDF (1 = a de cima, sem a marca d'água) e o trecho lido. */
    public record Origem(int pagina, int linha, String trecho) {
    }

    /** fonte: maior tamanho de fonte da linha, em pontos. */
    public record Linha(int pagina, int numero, float y, float fonte, List<Celula> celulas) {

        public String texto() {
            return celulas.stream().map(Celula::texto).collect(Collectors.joining(" "));
        }

        public Origem origem() {
            return new Origem(pagina, numero, texto());
        }
    }

    /** Linha de tabela (ou campo): texto de cada coluna, com as células quebradas já juntas, e a origem. */
    public record Registro(List<String> colunas, Origem origem) {

        public String get(int coluna) {
            return coluna < colunas.size() ? colunas.get(coluna) : "";
        }
    }

    public List<Linha> linhas() {
        return paginas.stream().flatMap(p -> p.linhas().stream()).toList();
    }

    public String texto() {
        return linhas().stream().map(Linha::texto).collect(Collectors.joining("\n"));
    }

    /** Todos os trechos aparecem no texto (reconhecimento do layout). */
    public boolean contem(String... trechos) {
        String texto = texto();
        return Arrays.stream(trechos).allMatch(texto::contains);
    }

    public Optional<Linha> linha(Pattern padrao) {
        return linhas().stream().filter(l -> padrao.matcher(l.texto()).find()).findFirst();
    }

    /**
     * Campo de uma tabela "rótulo | valor" (rótulo e valor podem estar quebrados): bloco cuja coluna da esquerda, junta,
     * é o rótulo. Colunas do registro: 0 = rótulo, 1 = valor.
     */
    public Optional<Registro> campo(String rotulo) {
        for (List<Linha> bloco : blocos(linhas())) {
            float xRotulo = bloco.stream().map(l -> l.celulas().getFirst().x()).min(Float::compare).orElse(0f);
            StringBuilder r = new StringBuilder();
            StringBuilder v = new StringBuilder();
            for (Linha l : bloco) {
                for (Celula c : l.celulas()) {
                    juntar(c.x() <= xRotulo + 2 ? r : v, c.texto());
                }
            }
            if (r.toString().equals(rotulo)) {
                return Optional.of(new Registro(List.of(r.toString(), v.toString()), origem(bloco, List.of(r.toString(), v.toString()))));
            }
        }
        return Optional.empty();
    }

    /** Linhas depois da linha de título (texto igual) até o próximo título conhecido; vazio se o título não existe. */
    public List<Linha> secao(String titulo, Set<String> titulos) {
        return secao(Pattern.compile(Pattern.quote(titulo)), titulos);
    }

    /** Como {@link #secao(String, Set)}, com a linha de início pelo padrão (texto inteiro). */
    public List<Linha> secao(Pattern inicio, Set<String> titulos) {
        List<Linha> secao = new ArrayList<>();
        boolean dentro = false;
        for (Linha l : linhas()) {
            if (dentro && titulos.contains(l.texto())) {
                break;
            }
            if (dentro) {
                secao.add(l);
            }
            dentro |= inicio.matcher(l.texto()).matches();
        }
        return secao;
    }

    /**
     * Tabela: o primeiro bloco é o cabeçalho (dá o início de cada coluna). Bloco cuja primeira coluna casa com a chave é
     * um registro; os demais (total, cabeçalho repetido na página seguinte, parágrafo) ficam de fora.
     */
    public static List<Registro> tabela(List<Linha> linhas, Pattern chave) {
        List<List<Linha>> blocos = blocos(linhas);
        if (blocos.isEmpty()) {
            return List.of();
        }
        List<Float> inicios = new ArrayList<>();
        blocos.getFirst().stream().flatMap(l -> l.celulas().stream()).map(Celula::x).sorted().forEach(x -> {
            if (inicios.isEmpty() || x - inicios.getLast() > 3) {
                inicios.add(x);
            }
        });
        List<Registro> registros = new ArrayList<>();
        for (List<Linha> bloco : blocos.subList(1, blocos.size())) {
            List<StringBuilder> textos = new ArrayList<>();
            inicios.forEach(x -> textos.add(new StringBuilder()));
            for (Linha l : bloco) {
                for (Celula c : l.celulas()) {
                    juntar(textos.get(coluna(inicios, c.x())), c.texto());
                }
            }
            List<String> colunas = textos.stream().map(StringBuilder::toString).toList();
            if (chave.matcher(colunas.getFirst()).matches()) {
                registros.add(new Registro(colunas, origem(bloco, colunas)));
            }
        }
        return registros;
    }

    /** Linhas seguidas (mesma página) a até VAO_BLOCO vezes a fonte uma da outra. */
    static List<List<Linha>> blocos(List<Linha> linhas) {
        List<List<Linha>> blocos = new ArrayList<>();
        Linha anterior = null;
        for (Linha l : linhas) {
            if (anterior == null || l.pagina() != anterior.pagina()
                    // menor fonte: a quebra dentro da célula repete a fonte; parágrafo maior logo abaixo não gruda
                    || l.y() - anterior.y() > VAO_BLOCO * Math.min(l.fonte(), anterior.fonte())) {
                blocos.add(new ArrayList<>());
            }
            blocos.getLast().add(l);
            anterior = l;
        }
        return blocos;
    }

    /**
     * Junta o pedaço seguinte de uma célula quebrada. Sem espaço quando a quebra cortou uma palavra (número, data ou
     * documento estreito demais para a coluna: "24/12/20" + "24", "07.18.24359.116912-" + "0").
     */
    private static void juntar(StringBuilder texto, String pedaco) {
        if (!texto.isEmpty() && !(PALAVRA_CORTADA_FIM.matcher(texto).matches() && PALAVRA_CORTADA_INICIO.matcher(pedaco).matches())) {
            texto.append(' ');
        }
        texto.append(pedaco);
    }

    /** A linha do bloco com mais células (a linha "do meio" da tabela) e o registro inteiro como trecho. */
    private static Origem origem(List<Linha> bloco, List<String> colunas) {
        Linha principal = bloco.stream().max(Comparator.comparingInt(l -> l.celulas().size())).orElseThrow();
        return new Origem(principal.pagina(), principal.numero(),
                colunas.stream().filter(c -> !c.isEmpty()).collect(Collectors.joining(" | ")));
    }

    private static int coluna(List<Float> inicios, float x) {
        int coluna = 0;
        for (int i = 0; i < inicios.size(); i++) {
            if (x >= inicios.get(i) - 3) {
                coluna = i;
            }
        }
        return coluna;
    }
}
