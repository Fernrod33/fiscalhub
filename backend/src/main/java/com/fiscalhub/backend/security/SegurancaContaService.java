package com.fiscalhub.backend.security;

import com.fiscalhub.backend.entity.Escritorio;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.repository.EscritorioRepository;
import com.fiscalhub.backend.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Sessões por dispositivo e o segundo fator (TOTP + códigos de recuperação de uso único). O filtro JWT confere, a cada
 * requisição, se a sessão do token segue aberta (UsuarioRepository.estadoToken).
 */
@Service
@Transactional
public class SegurancaContaService {

    public record Sessao(UUID id, LocalDateTime criadaEm, LocalDateTime ultimoUso, String ip, String dispositivo, boolean atual) {
    }

    public record Ativacao(String segredo, String uri, String qrSvg) {
    }

    public record CodigosRecuperacao(List<String> codigos) {
    }

    private static final int CODIGOS = 10;
    private static final String EMISSOR = "FiscalHub";

    private final JdbcTemplate jdbc;
    private final JwtUtils jwt;
    private final CifraDados cifra;
    private final UsuarioRepository usuarios;
    private final EscritorioRepository escritorios;
    private final PasswordEncoder senhas;

    public SegurancaContaService(JdbcTemplate jdbc, JwtUtils jwt, CifraDados cifra, UsuarioRepository usuarios,
                                 EscritorioRepository escritorios, PasswordEncoder senhas) {
        this.jdbc = jdbc;
        this.jwt = jwt;
        this.cifra = cifra;
        this.usuarios = usuarios;
        this.escritorios = escritorios;
        this.senhas = senhas;
    }

    // ---- sessões

    /** Abre uma sessão para o dispositivo da requisição e devolve o JWT dela (para o cookie). */
    public String abrirSessao(Usuario usuario, HttpServletRequest request) {
        return abrirSessao(usuario, request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
    }

    public String abrirSessao(Usuario usuario, String ip, String agente) {
        UUID id = UUID.randomUUID();
        LocalDateTime agora = LocalDateTime.now();
        jdbc.update("INSERT INTO sessao (id, usuario_id, criada_em, ultimo_uso, expira_em, ip, dispositivo) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, usuario.getId(), agora, agora, agora.plus(java.time.Duration.ofMillis(jwt.validadeMs())), ip,
                agente == null ? null : agente.substring(0, Math.min(agente.length(), 300)));
        return jwt.gerarToken(usuario, id);
    }

    @Transactional(readOnly = true)
    public List<Sessao> sessoes(UsuarioAutenticado ua) {
        return jdbc.query("""
                SELECT id, criada_em, ultimo_uso, ip, dispositivo FROM sessao
                WHERE usuario_id = ? AND revogada_em IS NULL AND expira_em > now() ORDER BY ultimo_uso DESC""",
                (rs, i) -> new Sessao(rs.getObject(1, UUID.class), rs.getTimestamp(2).toLocalDateTime(), rs.getTimestamp(3).toLocalDateTime(),
                        rs.getString(4), rs.getString(5), rs.getObject(1, UUID.class).equals(ua.sessaoId())), ua.id());
    }

    /** Encerra uma sessão do próprio usuário (404 se não for dele). */
    public void encerrar(UsuarioAutenticado ua, UUID sessaoId) {
        if (jdbc.update("UPDATE sessao SET revogada_em = now() WHERE id = ? AND usuario_id = ? AND revogada_em IS NULL", sessaoId, ua.id()) == 0) {
            throw new com.fiscalhub.backend.exception.ResourceNotFoundException("Sessão não encontrada.");
        }
    }

    /** Encerra todas as outras sessões; a atual continua. Devolve quantas. */
    public int encerrarOutras(UsuarioAutenticado ua) {
        return jdbc.update("UPDATE sessao SET revogada_em = now() WHERE usuario_id = ? AND revogada_em IS NULL AND id <> ?",
                ua.id(), ua.sessaoId());
    }

    /** Logout comum: só a sessão atual. */
    public void encerrarAtual(UsuarioAutenticado ua) {
        if (ua.sessaoId() != null) {
            jdbc.update("UPDATE sessao SET revogada_em = now() WHERE id = ? AND usuario_id = ?", ua.sessaoId(), ua.id());
        }
    }

    /** Último uso gravado no máximo a cada 5 minutos por sessão (o filtro chama a cada requisição). */
    public void registrarUso(UUID sessaoId) {
        jdbc.update("UPDATE sessao SET ultimo_uso = now() WHERE id = ? AND ultimo_uso < now() - interval '5 minutes'", sessaoId);
    }

    /** Sessões vencidas há mais de 30 dias saem do banco (as revogadas também, depois do mesmo prazo). */
    @Scheduled(cron = "0 30 3 * * *")
    public void limparSessoes() {
        jdbc.update("DELETE FROM sessao WHERE expira_em < now() - interval '30 days' OR revogada_em < now() - interval '30 days'");
    }

    // ---- segundo fator

    /** Gera um segredo novo (ainda inativo) e o QR para o aplicativo autenticador. */
    public Ativacao iniciarSegundoFator(UsuarioAutenticado ua) {
        Usuario u = usuario(ua);
        if (u.getTotpAtivo()) {
            throw new BusinessRuleException("O segundo fator já está ativo. Desative-o antes de cadastrar outro aplicativo.");
        }
        String segredo = Totp.novoSegredo();
        u.setTotpSegredo(cifra.cifrar(segredo));
        String uri = Totp.uri(EMISSOR, u.getEmail(), segredo);
        return new Ativacao(segredo, uri, Totp.qrSvg(uri));
    }

    /** Confirma o primeiro código do aplicativo, ativa e devolve os códigos de recuperação (mostrados uma vez só). */
    public CodigosRecuperacao ativarSegundoFator(UsuarioAutenticado ua, String codigo) {
        Usuario u = usuario(ua);
        if (u.getTotpAtivo() || u.getTotpSegredo() == null) {
            throw new BusinessRuleException("Comece a ativação do segundo fator de novo.");
        }
        OptionalLong passo = Totp.validar(cifra.decifrar(u.getTotpSegredo()), codigo, null, Instant.now());
        if (passo.isEmpty()) {
            throw new BusinessRuleException("Código inválido. Confira a hora do celular e digite o código atual do aplicativo.");
        }
        u.setTotpAtivo(true);
        u.setTotpUltimoPasso(passo.getAsLong());
        return novosCodigos(u);
    }

    /** Desativa (confere a senha). Não deixa se o escritório exige. */
    public void desativarSegundoFator(UsuarioAutenticado ua, String senha) {
        Usuario u = usuario(ua);
        conferirSenha(u, senha);
        if (u.getEscritorio().getExigirSegundoFator()) {
            throw new BusinessRuleException("O escritório exige o segundo fator; ele não pode ser desativado.");
        }
        u.setTotpAtivo(false);
        u.setTotpSegredo(null);
        u.setTotpUltimoPasso(null);
        jdbc.update("DELETE FROM codigo_recuperacao WHERE usuario_id = ?", u.getId());
    }

    public CodigosRecuperacao regenerarCodigos(UsuarioAutenticado ua, String senha) {
        Usuario u = usuario(ua);
        conferirSenha(u, senha);
        if (!u.getTotpAtivo()) {
            throw new BusinessRuleException("Ative o segundo fator antes.");
        }
        return novosCodigos(u);
    }

    /** No login: código do aplicativo (6 dígitos) ou um código de recuperação ainda não usado (que é consumido). */
    public boolean conferirSegundoFator(Usuario u, String codigo) {
        if (!u.getTotpAtivo() || codigo == null) {
            return false;
        }
        String limpo = codigo.replaceAll("[\\s-]", "");
        OptionalLong passo = Totp.validar(cifra.decifrar(u.getTotpSegredo()), limpo, u.getTotpUltimoPasso(), Instant.now());
        if (passo.isPresent()) {
            u.setTotpUltimoPasso(passo.getAsLong());
            usuarios.save(u);
            return true;
        }
        return jdbc.update("UPDATE codigo_recuperacao SET usado_em = now() WHERE usuario_id = ? AND hash = ? AND usado_em IS NULL",
                u.getId(), sha256(limpo.toUpperCase())) == 1;
    }

    // ---- escritório

    public boolean exigeSegundoFator(UsuarioAutenticado ua) {
        return escritorios.findById(ua.escritorioId()).map(Escritorio::getExigirSegundoFator).orElse(false);
    }

    /** ADMIN liga ou desliga para o escritório inteiro; quem ainda não ativou é levado a ativar na próxima requisição. */
    public void exigirSegundoFator(UsuarioAutenticado ua, boolean exigir) {
        Escritorio e = escritorios.findById(ua.escritorioId()).orElseThrow();
        if (exigir && !usuario(ua).getTotpAtivo()) {
            throw new BusinessRuleException("Ative o seu segundo fator antes de exigi-lo do escritório.");
        }
        e.setExigirSegundoFator(exigir);
    }

    private CodigosRecuperacao novosCodigos(Usuario u) {
        jdbc.update("DELETE FROM codigo_recuperacao WHERE usuario_id = ?", u.getId());
        List<String> codigos = new ArrayList<>();
        for (int i = 0; i < CODIGOS; i++) {
            String c = Totp.novoSegredo().substring(0, 10); // 50 bits, Base32 sem caracteres ambíguos de dígito
            codigos.add(c.substring(0, 5) + "-" + c.substring(5));
            jdbc.update("INSERT INTO codigo_recuperacao (usuario_id, hash) VALUES (?, ?)", u.getId(), sha256(c));
        }
        return new CodigosRecuperacao(codigos);
    }

    private void conferirSenha(Usuario u, String senha) {
        if (senha == null || !senhas.matches(senha, u.getSenha())) {
            throw new BusinessRuleException("Senha incorreta.");
        }
    }

    private Usuario usuario(UsuarioAutenticado ua) {
        return usuarios.findById(ua.id()).orElseThrow();
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
