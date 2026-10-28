package com.fiscalhub.backend.relatorio;

import com.fiscalhub.backend.entity.Escritorio;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.exception.ResourceNotFoundException;
import com.fiscalhub.backend.repository.EscritorioRepository;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.storage.ArmazenamentoArquivos;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Identidade do escritório nos relatórios: nome de exibição, logotipo, cor principal, rodapé e responsável técnico.
 * Logotipo: só PNG ou JPEG (pelo conteúdo, não pela extensão; SVG fica de fora por poder levar script), até 1 MB e
 * 4000 × 4000 px, redimensionado e regravado como PNG no servidor (some qualquer metadado ou carga embutida).
 */
@Service
public class IdentidadeEscritorioService {

    public static final long LOGO_MAX_BYTES = 1024 * 1024;
    private static final int LOGO_MAX_ORIGINAL = 4000;
    private static final int LOGO_LARGURA = 600;
    private static final int LOGO_ALTURA = 200;
    private static final Set<String> FORMATOS = Set.of("png", "jpeg");
    /** Contraste mínimo do texto normal (WCAG 2.1 AA). */
    public static final double CONTRASTE_AA = 4.5;
    public static final String COR_PADRAO = "#0b4f6c"; // --primary do design system

    public record Identidade(long escritorioId, String nome, String nomeExibicao, boolean temLogo, String corPrincipal,
                             String rodape, String responsavelNome, String responsavelRegistro) {
    }

    public record IdentidadeRequest(@Size(max = 150) String nomeExibicao,
                                    @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "use o formato #rrggbb") String corPrincipal,
                                    @Size(max = 500) String rodape, @Size(max = 150) String responsavelNome,
                                    @Size(max = 60) String responsavelRegistro) {
    }

    private final EscritorioRepository escritorios;
    private final ArmazenamentoArquivos armazenamento;

    public IdentidadeEscritorioService(EscritorioRepository escritorios, ArmazenamentoArquivos armazenamento) {
        this.escritorios = escritorios;
        this.armazenamento = armazenamento;
    }

    @Transactional(readOnly = true)
    public Identidade consultar(UsuarioAutenticado ua) {
        return identidade(escritorio(ua));
    }

    @Transactional
    public Identidade alterar(IdentidadeRequest r, UsuarioAutenticado ua) {
        Escritorio e = escritorio(ua);
        String cor = r.corPrincipal() == null || r.corPrincipal().isBlank() ? null : r.corPrincipal().toLowerCase();
        if (cor != null && contraste(cor, "#ffffff") < CONTRASTE_AA) {
            throw new BusinessRuleException("A cor " + cor + " tem contraste " + String.format("%.2f", contraste(cor, "#ffffff"))
                    + ":1 sobre o branco do relatório; o mínimo (WCAG AA) é 4,5:1. Escureça a cor.");
        }
        e.setNomeExibicao(vazioNulo(r.nomeExibicao()));
        e.setCorPrincipal(cor);
        e.setRodapeRelatorio(vazioNulo(r.rodape()));
        e.setResponsavelNome(vazioNulo(r.responsavelNome()));
        e.setResponsavelRegistro(vazioNulo(r.responsavelRegistro()));
        return identidade(e);
    }

    @Transactional
    public Identidade gravarLogo(MultipartFile arquivo, UsuarioAutenticado ua) {
        if (arquivo.isEmpty() || arquivo.getSize() > LOGO_MAX_BYTES) {
            throw new BusinessRuleException("O logotipo deve ter até 1 MB.");
        }
        byte[] png;
        try (InputStream in = arquivo.getInputStream()) {
            png = normalizarLogo(in.readNBytes((int) LOGO_MAX_BYTES + 1));
        } catch (IOException ex) {
            throw new BusinessRuleException("Não foi possível ler o logotipo. Use PNG ou JPEG.", ex);
        }
        Escritorio e = escritorio(ua);
        String chave = "escritorios/" + e.getId() + "/identidade/logo-" + UUID.randomUUID() + ".png";
        armazenamento.salvar(chave, new ByteArrayInputStream(png), png.length, "image/png");
        // ponytail: o logotipo anterior fica no storage (relatórios emitidos o referenciam no snapshot); limpar órfãos se o volume pesar
        e.setLogoChave(chave);
        return identidade(e);
    }

    @Transactional(readOnly = true)
    public byte[] logo(UsuarioAutenticado ua) {
        String chave = escritorio(ua).getLogoChave();
        if (chave == null) {
            throw new ResourceNotFoundException("O escritório não tem logotipo.");
        }
        return ler(chave);
    }

    @Transactional(readOnly = true)
    public String chaveLogo(UsuarioAutenticado ua) {
        return escritorio(ua).getLogoChave();
    }

    /** Logotipo de uma chave como data URI (o HTML e o PDF não buscam nada fora do documento). */
    public String dataUri(String chave) {
        return chave == null ? null : "data:image/png;base64," + Base64.getEncoder().encodeToString(ler(chave));
    }

    private byte[] ler(String chave) {
        try (InputStream in = armazenamento.abrir(chave)) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Confere o formato pelo conteúdo e as dimensões antes de decodificar; reduz para caber em 600 × 200 e grava PNG. */
    static byte[] normalizarLogo(byte[] bytes) throws IOException {
        if (bytes.length > LOGO_MAX_BYTES) {
            throw new BusinessRuleException("O logotipo deve ter até 1 MB.");
        }
        BufferedImage original;
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(iis);
            ImageReader leitor = leitores.hasNext() ? leitores.next() : null;
            if (leitor == null || !FORMATOS.contains(leitor.getFormatName().toLowerCase())) {
                throw new BusinessRuleException("Formato de logotipo não aceito. Use PNG ou JPEG (SVG não é aceito).");
            }
            try {
                leitor.setInput(iis, true, true);
                if (leitor.getWidth(0) > LOGO_MAX_ORIGINAL || leitor.getHeight(0) > LOGO_MAX_ORIGINAL) {
                    throw new BusinessRuleException("O logotipo deve ter no máximo 4000 × 4000 px.");
                }
                original = leitor.read(0);
            } finally {
                leitor.dispose();
            }
        }
        double escala = Math.min(1, Math.min((double) LOGO_LARGURA / original.getWidth(), (double) LOGO_ALTURA / original.getHeight()));
        int w = Math.max(1, (int) Math.round(original.getWidth() * escala));
        int h = Math.max(1, (int) Math.round(original.getHeight() * escala));
        BufferedImage destino = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = destino.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(original, 0, 0, w, h, null);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(destino, "png", saida);
        return saida.toByteArray();
    }

    /** Razão de contraste WCAG 2.1 entre duas cores #rrggbb (1 a 21). Mesma conta do frontend (lib/contraste.ts). */
    public static double contraste(String a, String b) {
        double la = luminancia(a);
        double lb = luminancia(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminancia(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double r = canal((rgb >> 16) & 0xff);
        double g = canal((rgb >> 8) & 0xff);
        double b = canal(rgb & 0xff);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double canal(int c) {
        double s = c / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    private Escritorio escritorio(UsuarioAutenticado ua) {
        return escritorios.findById(ua.escritorioId()).orElseThrow(() -> new ResourceNotFoundException("Escritório não encontrado."));
    }

    private static Identidade identidade(Escritorio e) {
        return new Identidade(e.getId(), e.getNome(), e.getNomeExibicao(), e.getLogoChave() != null,
                e.getCorPrincipal() == null ? COR_PADRAO : e.getCorPrincipal(), e.getRodapeRelatorio(), e.getResponsavelNome(),
                e.getResponsavelRegistro());
    }

    private static String vazioNulo(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
