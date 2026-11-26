package com.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GatewayApplicationContextTests {

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private Tracer tracer;

    @Test
    void startsWithTheInstrumentedRestClientBuilder() {
        assertNotNull(restClientBuilder);
    }

    @Test
    void addsTraceAndSpanIdsToLogContext() {
        Span span = tracer.nextSpan().name("test-trace").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            assertFalse(span.context().traceId().isBlank());
            assertFalse(span.context().spanId().isBlank());
            assertEquals(span.context().traceId(), MDC.get("traceId"));
            assertEquals(span.context().spanId(), MDC.get("spanId"));
        } finally {
            span.end();
        }
    }
}
