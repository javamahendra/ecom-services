package com.app;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "eureka.client.enabled=false")
class GatewayApplicationContextTests {

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Test
    void startsWithTheInstrumentedRestClientBuilder() {
        assertNotNull(restClientBuilder);
    }
}
