package com.fiscalhub.backend.security;

import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.Papel;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    private final SecretKey signingKey;
    private static final String FASE = "fase";
    private final int jwtExpirationMs;

    // Keys.hmacShaKeyFor lanca WeakKeyException se o segredo tiver menos de 256 bits: o app nao sobe.
    public JwtUtils(@Value("${app.jwt.secret}") String jwtSecret,
                    @Value("${app.jwt.expiration-ms}") int jwtExpirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationMs = jwtExpirationMs;
    }

    /** Token da sessão (cookie): usuário, escritório, papel, versão do token e o id da sessão no servidor. */
    public String gerarToken(Usuario usuario, UUID sessaoId) {
        Date agora = new Date();
        return Jwts.builder()
                .setSubject(usuario.getEmail())
                .claim("uid", usuario.getId())
                .claim("escritorioId", usuario.getEscritorio().getId())
                .claim("papel", usuario.getPapel().name())
                .claim("tv", usuario.getVersaoToken())
                .claim("sid", sessaoId.toString())
                .setIssuedAt(agora)
                .setExpiration(new Date(agora.getTime() + jwtExpirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Validade do token de sessão (a do registro da sessão no banco é a mesma). */
    public long validadeMs() {
        return jwtExpirationMs;
    }

    /** Token válido => usuário das claims; inválido, expirado, sem as claims ou desafio do 2º fator => vazio. */
    public Optional<UsuarioAutenticado> autenticar(String token) {
        try {
            Claims c = Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();
            if (c.get(FASE) != null) {
                return Optional.empty(); // o desafio do 2º fator não é sessão
            }
            return Optional.of(new UsuarioAutenticado(c.get("uid", Long.class), c.getSubject(),
                    c.get("escritorioId", Long.class), Papel.valueOf(c.get("papel", String.class)),
                    c.get("tv", Integer.class), UUID.fromString(c.get("sid", String.class))));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            logger.warn("Token JWT rejeitado: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Desafio do 2º fator: senha conferida, falta o código. Vale 5 minutos e não abre sessão. */
    public String gerarDesafio(Usuario usuario) {
        Date agora = new Date();
        return Jwts.builder().setSubject(usuario.getEmail()).claim("uid", usuario.getId()).claim("tv", usuario.getVersaoToken())
                .claim(FASE, "2fa").setIssuedAt(agora).setExpiration(new Date(agora.getTime() + 5 * 60_000))
                .signWith(signingKey, SignatureAlgorithm.HS256).compact();
    }

    /** O id do usuário do desafio válido (e a versão do token, para a troca de senha no meio invalidar). */
    public Optional<long[]> lerDesafio(String desafio) {
        try {
            Claims c = Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(desafio).getBody();
            return "2fa".equals(c.get(FASE)) ? Optional.of(new long[]{c.get("uid", Long.class), c.get("tv", Integer.class)}) : Optional.empty();
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
