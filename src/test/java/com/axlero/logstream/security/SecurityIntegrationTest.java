package com.axlero.logstream.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class SecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(springSecurityFilterChain)
                .build();
    }

    @Test
    void testPublicTokenGenerationEndpoint() throws Exception {
        String jsonPayload = """
            {
                "username": "test-admin",
                "roles": ["ROLE_ADMIN"]
            }
            """;

        mockMvc.perform(post("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("test-admin"));
    }

    @Test
    void testAlertRulesRejectsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/alerts/rules"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAlertRulesAllowedForAdmin() throws Exception {
        String adminToken = tokenProvider.generateToken("admin", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc.perform(get("/api/alerts/rules")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void testAlertRulesForbiddenForViewer() throws Exception {
        String viewerToken = tokenProvider.generateToken("viewer", List.of(new SimpleGrantedAuthority("ROLE_VIEWER")));

        mockMvc.perform(get("/api/alerts/rules")
                .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
    }
}
