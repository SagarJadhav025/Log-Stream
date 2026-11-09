package com.axlero.logstream.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, CorsConfigurationSource corsConfigurationSource) {
        this.jwtFilter = jwtFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                    .preload(true)
                )
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self' ws: wss:; frame-ancestors 'none';")
                )
            )
            .authorizeHttpRequests(auth -> auth
                // Allow static resources, swagger/actuator, and test mock receiver
                .requestMatchers("/", "/index.html", "/static/**", "/assets/**", "/favicon.ico").permitAll()
                .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                .requestMatchers("/api/alerts/mock-receiver", "/api/alerts/received").permitAll()

                // WebSocket handshake is verified by JwtWebSocketHandshakeInterceptor
                .requestMatchers("/ws/**").permitAll()

                // Alert configurations & mutations require ROLE_ADMIN
                .requestMatchers("/api/alerts/**").hasRole("ADMIN")

                // Search & histogram queries allow VIEWERS or ADMINs
                .requestMatchers(HttpMethod.GET, "/api/logs/**", "/api/search/**")
                    .hasAnyRole("VIEWER", "ADMIN", "INGESTOR")

                // Log Ingestion via REST
                .requestMatchers(HttpMethod.POST, "/api/logs/**")
                    .hasAnyRole("INGESTOR", "ADMIN")

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
