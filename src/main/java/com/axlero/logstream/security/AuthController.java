package com.axlero.logstream.security;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtTokenProvider tokenProvider;

    public AuthController(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    /**
     * Endpoint to obtain a token for demo, testing, or user login.
     * POST /api/auth/token with {"username": "admin", "roles": ["ROLE_ADMIN"]}
     */
    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> generateToken(@RequestBody Map<String, Object> request) {
        String username = (String) request.getOrDefault("username", "demo-user");
        List<?> rawRoles = (List<?>) request.getOrDefault("roles", List.of("ROLE_VIEWER"));

        List<SimpleGrantedAuthority> authorities = rawRoles.stream()
                .map(r -> new SimpleGrantedAuthority(r.toString()))
                .collect(Collectors.toList());

        String token = tokenProvider.generateToken(username, authorities);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "type", "Bearer",
                "username", username,
                "roles", rawRoles
        ));
    }
}
