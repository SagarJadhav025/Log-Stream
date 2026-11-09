package com.axlero.logstream.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private final String secret = "dGhpc0lzQVZlcnlTZWN1cmVTZWNyZXRLZXlGb3JMb2dTdHJlYW0yMDI2IQ==";

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(secret, 3600000L);
    }

    @Test
    void testGenerateAndValidateToken() {
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_VIEWER")
        );

        String token = tokenProvider.generateToken("secops-user", authorities);
        assertNotNull(token);
        assertTrue(tokenProvider.validateToken(token));

        Authentication auth = tokenProvider.getAuthentication(token);
        assertEquals("secops-user", auth.getName());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_VIEWER")));
    }

    @Test
    void testTamperedTokenFails() {
        String token = tokenProvider.generateToken("user1", List.of(new SimpleGrantedAuthority("ROLE_VIEWER")));
        String tampered = token + "xyz";

        assertFalse(tokenProvider.validateToken(tampered));
    }
}
