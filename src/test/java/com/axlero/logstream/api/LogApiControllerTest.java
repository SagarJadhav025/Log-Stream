package com.axlero.logstream.api;

import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogApiControllerTest {

    private TimeSeriesService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        service = mock(TimeSeriesService.class);

        when(service.histogram(
                anyLong(),
                anyLong(),
                anyLong(),
                any(Query.class)
        )).thenReturn(List.<Map<String, Object>>of());

        LogApiController controller = new LogApiController(service);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    void appliesLevelFilter() throws Exception {
        Query query = executeAndCapture(
                "/api/logs/histogram?level=ERROR"
        );

        assertInstanceOf(BooleanQuery.class, query);
        assertTrue(query.toString().contains("level:ERROR"));
    }

    @Test
    void appliesServiceNameFilter() throws Exception {
        Query query = executeAndCapture(
                "/api/logs/histogram?serviceName=billing-api"
        );

        assertInstanceOf(BooleanQuery.class, query);
        assertTrue(query.toString().contains("service:billing-api"));
    }

    @Test
    void appliesTextFilter() throws Exception {
        Query query = executeAndCapture(
                "/api/logs/histogram?text=timeout"
        );

        assertInstanceOf(BooleanQuery.class, query);
        assertTrue(query.toString().contains("message:timeout"));
    }

    @Test
    void combinesAllFiltersUsingMustClauses() throws Exception {
        Query query = executeAndCapture(
                "/api/logs/histogram?level=ERROR"
                        + "&serviceName=billing-api"
                        + "&text=timeout"
        );

        BooleanQuery booleanQuery =
                assertInstanceOf(BooleanQuery.class, query);

        assertEquals(3, booleanQuery.clauses().size());

        assertTrue(query.toString().contains("+level:ERROR"));
        assertTrue(query.toString().contains("+service:billing-api"));
        assertTrue(query.toString().contains("+message:timeout"));
    }

    @Test
    void usesMatchAllQueryWhenNoFiltersAreProvided() throws Exception {
        Query query = executeAndCapture("/api/logs/histogram");

        assertInstanceOf(MatchAllDocsQuery.class, query);
    }

    @Test
    void passesDefaultBucketSizeToService() throws Exception {
        mockMvc.perform(get("/api/logs/histogram"))
                .andExpect(status().isOk());

        verify(service).histogram(
                anyLong(),
                anyLong(),
                eq(60_000L),
                any(Query.class)
        );
    }

    @Test
    void rejectsZeroMinutes() throws Exception {
        mockMvc.perform(get("/api/logs/histogram")
                        .param("minutes", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNegativeMinutes() throws Exception {
        mockMvc.perform(get("/api/logs/histogram")
                        .param("minutes", "-10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsZeroBucketSeconds() throws Exception {
        mockMvc.perform(get("/api/logs/histogram")
                        .param("bucketSeconds", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNegativeBucketSeconds() throws Exception {
        mockMvc.perform(get("/api/logs/histogram")
                        .param("bucketSeconds", "-5"))
                .andExpect(status().isBadRequest());
    }

    private Query executeAndCapture(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isOk());

        ArgumentCaptor<Query> queryCaptor =
                ArgumentCaptor.forClass(Query.class);

        verify(service).histogram(
                anyLong(),
                anyLong(),
                anyLong(),
                queryCaptor.capture()
        );

        return queryCaptor.getValue();
    }
}
