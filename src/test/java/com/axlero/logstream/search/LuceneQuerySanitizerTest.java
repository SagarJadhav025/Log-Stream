package com.axlero.logstream.search;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LuceneQuerySanitizerTest {

    @Test
    void testSanitizeEscapesLuceneMetacharacters() {
        String unsafe = "error: [database] failed AND (retry > 5) \"unclosed";
        String sanitized = LuceneQuerySanitizer.sanitize(unsafe);

        assertNotNull(sanitized);
        // Reserved metacharacters must be escaped with backslash
        assertTrue(sanitized.contains("\\["));
        assertTrue(sanitized.contains("\\]"));
        assertTrue(sanitized.contains("\\("));
        assertTrue(sanitized.contains("\\)"));
        assertTrue(sanitized.contains("\\:"));
    }

    @Test
    void testSanitizeStripsLeadingWildcards() {
        String unsafe = "*error ?timeout *critical";
        String sanitized = LuceneQuerySanitizer.sanitize(unsafe);

        assertFalse(sanitized.startsWith("*"));
        assertFalse(sanitized.startsWith("?"));
    }

    @Test
    void testSanitizeStripsInternalFields() {
        String unsafe = "timestamp_epoch_millis:100000 message:test id:secret-id";
        String sanitized = LuceneQuerySanitizer.sanitize(unsafe);

        assertFalse(sanitized.contains("timestamp_epoch_millis:"));
        assertFalse(sanitized.contains("id:"));
    }

    @Test
    void testSanitizeLuceneSyntaxPreservesAllowedFields() {
        String query = "level:ERROR AND service:billing-api";
        String sanitized = LuceneQuerySanitizer.sanitizeLuceneSyntax(query);

        assertEquals("level:ERROR AND service:billing-api", sanitized);
    }

    @Test
    void testSanitizeLuceneSyntaxBalancesQuotes() {
        String unclosedQuote = "level:ERROR AND \"unclosed quote message";
        String sanitized = LuceneQuerySanitizer.sanitizeLuceneSyntax(unclosedQuote);

        assertFalse(sanitized.contains("\""));
    }
}
