package com.axlero.logstream.search;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Day 7: Verifies the 5-second search timeout circuit-breaker in SearchApiController.
 */
class SearchTimeoutTest {

    @Test
    void slowSearchReturns408RequestTimeout() {
        Logsearchservice mockService = mock(Logsearchservice.class);
        doAnswer(invocation -> {
            TimeUnit.SECONDS.sleep(6);
            return List.of();
        }).when(mockService).search(any());

        SearchApiController controller = new SearchApiController(mockService);
        ResponseEntity<Map<String, Object>> response = controller.search("slow-query", 50, null, null, null);

        assertEquals(HttpStatus.REQUEST_TIMEOUT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(String.valueOf(response.getBody().get("error")).contains("timed out"));
    }

    @Test
    void fastSearchReturns200WithResults() {
        Logsearchservice mockService = mock(Logsearchservice.class);
        when(mockService.search(any())).thenReturn(List.of());

        SearchApiController controller = new SearchApiController(mockService);
        ResponseEntity<Map<String, Object>> response = controller.search("timeout", 50, null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().get("count"));
    }
}
