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

@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    private final SecretKey signingKey;
    private final int jwtExpirationMs;

    // Keys.hmacShaKeyFor lanca WeakKeyException se o segredo tiver menos de 256 bits: o app nao sobe.
    public JwtUtils(@Value("${app.jwt.secret}") String jwtSecret,
                    @Value("${app.jwt.expiration-ms}") int jwtExpirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationMs = jwtExpirationMs;
    }

    public String gerarToken(Usuario usuario) {
        Date agora = new Date();
        return Jwts.builder()
                .setSubject(usuario.getEmail())
                .claim("uid", usuario.getId())
                .claim("escritorioId", usuario.getEscritorio().getId())
                .claim("papel", usuario.getPapel().name())
                .setIssuedAt(agora)
                .setExpiration(new Date(agora.getTime() + jwtExpirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Token válido => usuário das claims; inválido, expirado ou sem as claims => vazio. */
    public Optional<UsuarioAutenticado> autenticar(String token) {
        try {
            Claims c = Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();
            return Optional.of(new UsuarioAutenticado(c.get("uid", Long.class), c.getSubject(),
                    c.get("escritorioId", Long.class), Papel.valueOf(c.get("papel", String.class))));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            logger.warn("Token JWT rejeitado: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
