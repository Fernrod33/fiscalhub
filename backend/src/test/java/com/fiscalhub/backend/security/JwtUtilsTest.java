package com.fiscalhub.backend.security;

import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

    @Test
    void recusaSegredoComMenosDe256Bits() {
        assertThatThrownBy(() -> new JwtUtils("curto-demais", 60_000))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    void geraEValidaTokenComSegredoValido() {
        JwtUtils jwtUtils = new JwtUtils("x".repeat(32), 60_000);
        String token = jwtUtils.generateTokenFromUsername("a@a.com");
        assertThat(jwtUtils.validateJwtToken(token)).isTrue();
        assertThat(jwtUtils.getUserNameFromJwtToken(token)).isEqualTo("a@a.com");
    }
}
