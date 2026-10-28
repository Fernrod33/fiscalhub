package com.fiscalhub.backend.relatorio;

import com.fiscalhub.backend.relatorio.ModeloRelatorio.Grafico;
import com.fiscalhub.backend.relatorio.ModeloRelatorio.Serie;
import com.fiscalhub.backend.relatorio.ModeloRelatorio.TipoGrafico;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Gráficos dos relatórios em SVG (barras, barras empilhadas, linha), gerados no backend e embutidos no HTML e no PDF.
 * Paleta: a categórica do design system (--chart-1..6 em globals.css, tema claro), validada para daltonismo e
 * contraste ≥ 3:1 no branco; além da cor, cada série tem um padrão (hachura) e a legenda. Todo texto passa por esc():
 * nomes de série e categorias podem vir de arquivo importado.
 */
public final class GraficoSvg {

    public static final List<String> PALETA = List.of("#2a78d6", "#d95926", "#1b9e77", "#c98500", "#6250d6", "#d55181");
    private static final int L = 640;
    private static final int A = 240;
    private static final int ESQ = 96;
    private static final int DIR = 12;
    private static final int TOPO = 12;
    private static final int BASE = 40;
    private static final String TINTA = "#0f1c2e";
    private static final String TINTA_2 = "#556378";
    private static final String GRADE = "#d7dee7";

    private GraficoSvg() {
    }

    public static String svg(Grafico g) {
        List<Serie> series = g.series().stream().limit(PALETA.size()).toList();
        int n = g.categorias().size();
        double max = 0;
        double min = 0;
        for (int c = 0; c < n; c++) {
            double pilha = 0;
            for (Serie s : series) {
                double v = valor(s, c);
                max = Math.max(max, g.tipo() == TipoGrafico.EMPILHADAS ? (pilha += Math.max(v, 0)) : v);
                min = Math.min(min, v);
            }
        }
        double passo = passo((max - min) / 4);
        double topo = Math.max(passo, Math.ceil(max / passo) * passo);
        double fundo = Math.floor(min / passo) * passo;
        int legenda = series.size() > 1 ? 22 : 0;
        int altura = A + legenda;
        double plotA = A - TOPO - BASE;
        double plotL = L - ESQ - DIR;
        StringBuilder s = new StringBuilder();
        s.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(L).append("\" height=\"").append(altura)
                .append("\" viewBox=\"0 0 ").append(L).append(' ').append(altura).append("\" role=\"img\" aria-label=\"")
                .append(esc(g.titulo())).append("\" font-family=\"Inter, sans-serif\" font-size=\"10\">");
        s.append("<defs>");
        for (int i = 0; i < PALETA.size(); i++) {
            s.append(padrao(i));
        }
        s.append("</defs>");
        // grade e eixo y (recessivos)
        for (double t = fundo; t <= topo + passo / 2; t += passo) {
            double y = TOPO + plotA * (topo - t) / (topo - fundo);
            s.append(linha(ESQ, y, L - DIR, y, Math.abs(t) < 1e-9 ? TINTA_2 : GRADE));
            s.append(texto(ESQ - 6, y + 3, "end", TINTA_2, Formatos.celula(BigDecimal.valueOf(t).stripTrailingZeros().toPlainString(), g.formato())));
        }
        double banda = plotL / Math.max(n, 1);
        for (int c = 0; c < n; c++) {
            double x0 = ESQ + banda * c;
            s.append(texto(x0 + banda / 2, TOPO + plotA + 14, "middle", TINTA, g.categorias().get(c)));
        }
        java.util.function.DoubleUnaryOperator y = v -> TOPO + plotA * (topo - v) / (topo - fundo);
        if (g.tipo() == TipoGrafico.LINHA) {
            StringBuilder marcadores = new StringBuilder(); // pontos e rótulos por cima das linhas
            for (int i = 0; i < series.size(); i++) {
                Serie serie = series.get(i);
                StringBuilder pontos = new StringBuilder();
                for (int c = 0; c < n; c++) {
                    BigDecimal v = c < serie.valores().size() ? serie.valores().get(c) : null;
                    if (v != null) {
                        double cx = ESQ + banda * c + banda / 2;
                        double cy = y.applyAsDouble(v.doubleValue());
                        pontos.append(String.format(Locale.ROOT, "%.1f,%.1f ", cx, cy));
                        marcadores.append(String.format(Locale.ROOT, "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"4\" fill=\"%s\" stroke=\"#ffffff\" stroke-width=\"2\"><title>%s</title></circle>",
                                cx, cy, PALETA.get(i), esc(serie.nome() + " " + g.categorias().get(c) + ": " + Formatos.celula(v.toPlainString(), g.formato()))));
                        marcadores.append(texto(cx, cy - 8, "middle", TINTA, Formatos.celula(v.toPlainString(), g.formato())));
                    }
                }
                s.append("<polyline fill=\"none\" stroke=\"").append(PALETA.get(i)).append("\" stroke-width=\"2\" points=\"")
                        .append(pontos.toString().trim()).append("\"/>");
            }
            s.append(marcadores);
        } else {
            boolean empilhada = g.tipo() == TipoGrafico.EMPILHADAS;
            int grupos = empilhada ? 1 : series.size();
            double largura = Math.min(28, (banda * 0.7) / grupos);
            for (int c = 0; c < n; c++) {
                double x0 = ESQ + banda * c + (banda - largura * grupos - 2 * (grupos - 1)) / 2;
                double pilha = 0;
                for (int i = 0; i < series.size(); i++) {
                    double v = valor(series.get(i), c);
                    if (v == 0) {
                        continue;
                    }
                    double base = empilhada ? pilha : 0;
                    double yTopo = y.applyAsDouble(Math.max(base + v, base));
                    double yBase = y.applyAsDouble(Math.min(base + v, base));
                    double x = empilhada ? x0 : x0 + i * (largura + 2);
                    // 2 px de vão entre segmentos empilhados (cor da superfície)
                    double h = Math.max(1, yBase - yTopo - (empilhada && pilha > 0 ? 2 : 0));
                    s.append(String.format(Locale.ROOT, "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" fill=\"url(#p%d)\"><title>%s</title></rect>",
                            x, yTopo, largura, h, i, esc(series.get(i).nome() + " " + g.categorias().get(c) + ": "
                                    + Formatos.celula(BigDecimal.valueOf(v).toPlainString(), g.formato()))));
                    pilha += empilhada ? v : 0;
                }
            }
        }
        if (legenda > 0) {
            double x = ESQ;
            for (int i = 0; i < series.size(); i++) {
                s.append(String.format(Locale.ROOT, "<rect x=\"%.1f\" y=\"%d\" width=\"12\" height=\"12\" fill=\"url(#p%d)\"/>", x, A + 4, i));
                s.append(texto(x + 16, A + 14, "start", TINTA, series.get(i).nome()));
                x += 28 + series.get(i).nome().length() * 6;
            }
        }
        return s.append("</svg>").toString();
    }

    /** Padrão da série i: cor sólida na 1ª, hachuras diferentes nas demais (identidade sem depender só da cor). */
    private static String padrao(int i) {
        String cor = PALETA.get(i);
        String fundo = "<rect width=\"6\" height=\"6\" fill=\"" + cor + "\"/>";
        String marca = switch (i) {
            case 0 -> "";
            case 1 -> "<path d=\"M0,6 L6,0\" stroke=\"#ffffff\" stroke-width=\"1.2\"/>";
            case 2 -> "<path d=\"M0,0 L6,6\" stroke=\"#ffffff\" stroke-width=\"1.2\"/>";
            case 3 -> "<circle cx=\"3\" cy=\"3\" r=\"1.1\" fill=\"#ffffff\"/>";
            case 4 -> "<path d=\"M0,3 L6,3\" stroke=\"#ffffff\" stroke-width=\"1.2\"/>";
            default -> "<path d=\"M3,0 L3,6\" stroke=\"#ffffff\" stroke-width=\"1.2\"/>";
        };
        return "<pattern id=\"p" + i + "\" width=\"6\" height=\"6\" patternUnits=\"userSpaceOnUse\">" + fundo + marca + "</pattern>";
    }

    private static double valor(Serie s, int c) {
        return c < s.valores().size() && s.valores().get(c) != null ? s.valores().get(c).doubleValue() : 0;
    }

    /** Passo "redondo" (1, 2, 2,5 ou 5 × 10^k) maior ou igual ao bruto. */
    static double passo(double bruto) {
        if (bruto <= 0) {
            return 1;
        }
        double p = Math.pow(10, Math.floor(Math.log10(bruto)));
        for (double m : new double[]{1, 2, 2.5, 5, 10}) {
            if (m * p >= bruto) {
                return m * p;
            }
        }
        return 10 * p;
    }

    private static String linha(double x1, double y1, double x2, double y2, String cor) {
        return String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"%s\" stroke-width=\"1\"/>", x1, y1, x2, y2, cor);
    }

    private static String texto(double x, double y, String ancora, String cor, String t) {
        return String.format(Locale.ROOT, "<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"%s\" fill=\"%s\">%s</text>", x, y, ancora, cor, esc(t));
    }

    /** Escape XML de todo texto que entra no SVG. */
    static String esc(String t) {
        if (t == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(t.length());
        for (char ch : t.toCharArray()) {
            switch (ch) {
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '&' -> b.append("&amp;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(ch < 0x20 && ch != '\n' && ch != '\t' ? ' ' : ch);
            }
        }
        return b.toString();
    }
}
