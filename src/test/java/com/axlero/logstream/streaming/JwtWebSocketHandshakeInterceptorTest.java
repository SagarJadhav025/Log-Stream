
package com.axlero.logstream.streaming;

import com.axlero.logstream.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtWebSocketHandshakeInterceptorTest {

    private JwtTokenProvider tokenProvider;
    private JwtWebSocketHandshakeInterceptor interceptor;
    private ServerHttpResponse response;
    private WebSocketHandler wsHandler;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        tokenProvider = mock(JwtTokenProvider.class);
        interceptor = new JwtWebSocketHandshakeInterceptor(tokenProvider);
        response = mock(ServerHttpResponse.class);
        wsHandler = mock(WebSocketHandler.class);
        attributes = new HashMap<>();
    }

    private Authentication createAuthentication(String username, String role) {
        return new UsernamePasswordAuthenticationToken(
                username,
                "test-token",
                List.of(new SimpleGrantedAuthority(role))
        );
    }

    private ServletServerHttpRequest createRequest(String token, String authorizationHeader) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        if (token != null) {
            servletRequest.setParameter("token", token);
        }

        if (authorizationHeader != null) {
            servletRequest.addHeader("Authorization", authorizationHeader);
        }

        return new ServletServerHttpRequest(servletRequest);
    }

    @Test
    void acceptsValidViewerTokenFromQueryParameter() {
        Authentication auth = createAuthentication("viewer-user", "ROLE_VIEWER");

        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getAuthentication("valid-token")).thenReturn(auth);

        boolean result = interceptor.beforeHandshake(
                createRequest("valid-token", null),
                response,
                wsHandler,
                attributes
        );

        assertTrue(result);
        assertEquals(auth, attributes.get("auth"));
        assertEquals("viewer-user", attributes.get("username"));
        verify(response, never()).setStatusCode(any());
    }

    @Test
    void acceptsValidAdminTokenFromQueryParameter() {
        Authentication auth = createAuthentication("admin-user", "ROLE_ADMIN");

        when(tokenProvider.validateToken("admin-token")).thenReturn(true);
        when(tokenProvider.getAuthentication("admin-token")).thenReturn(auth);

        boolean result = interceptor.beforeHandshake(
                createRequest("admin-token", null),
                response,
                wsHandler,
                attributes
        );

        assertTrue(result);
        assertEquals("admin-user", attributes.get("username"));
    }

    @Test
    void acceptsValidBearerTokenFromAuthorizationHeader() {
        Authentication auth = createAuthentication("header-user", "ROLE_VIEWER");

        when(tokenProvider.validateToken("header-token")).thenReturn(true);
        when(tokenProvider.getAuthentication("header-token")).thenReturn(auth);

        boolean result = interceptor.beforeHandshake(
                createRequest(null, "Bearer header-token"),
                response,
                wsHandler,
                attributes
        );

        assertTrue(result);
        assertEquals("header-user", attributes.get("username"));
        verify(tokenProvider).validateToken("header-token");
    }

    @Test
    void fallsBackToAuthorizationHeaderWhenQueryTokenIsBlank() {
        Authentication auth = createAuthentication("fallback-user", "ROLE_ADMIN");

        when(tokenProvider.validateToken("fallback-token")).thenReturn(true);
        when(tokenProvider.getAuthentication("fallback-token")).thenReturn(auth);

        boolean result = interceptor.beforeHandshake(
                createRequest("   ", "Bearer fallback-token"),
                response,
                wsHandler,
                attributes
        );

        assertTrue(result);
        assertEquals("fallback-user", attributes.get("username"));
    }

    @Test
    void rejectsMissingToken() {
        boolean result = interceptor.beforeHandshake(
                createRequest(null, null),
                response,
                wsHandler,
                attributes
        );

        assertFalse(result);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(tokenProvider);
    }

    @Test
    void rejectsInvalidToken() {
        when(tokenProvider.validateToken("invalid-token")).thenReturn(false);

        boolean result = interceptor.beforeHandshake(
                createRequest("invalid-token", null),
                response,
                wsHandler,
                attributes
        );

        assertFalse(result);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(tokenProvider, never()).getAuthentication(anyString());
    }

    @Test
    void rejectsUserWithUnauthorizedRole() {
        Authentication auth = createAuthentication("guest-user", "ROLE_GUEST");

        when(tokenProvider.validateToken("guest-token")).thenReturn(true);
        when(tokenProvider.getAuthentication("guest-token")).thenReturn(auth);

        boolean result = interceptor.beforeHandshake(
                createRequest("guest-token", null),
                response,
                wsHandler,
                attributes
        );

        assertFalse(result);
        assertTrue(attributes.isEmpty());
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsAuthorizationHeaderWithoutBearerPrefix() {
        boolean result = interceptor.beforeHandshake(
                createRequest(null, "Basic some-credentials"),
                response,
                wsHandler,
                attributes
        );

        assertFalse(result);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(tokenProvider);
    }

    @Test
    void rejectsNonServletHttpRequest() {
        org.springframework.http.server.ServerHttpRequest request =
                mock(org.springframework.http.server.ServerHttpRequest.class);

        boolean result = interceptor.beforeHandshake(
                request,
                response,
                wsHandler,
                attributes
        );

        assertFalse(result);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(tokenProvider);
    }
}
