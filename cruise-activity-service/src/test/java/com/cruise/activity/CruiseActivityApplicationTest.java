package com.cruise.activity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.context.annotation.Import(com.cruise.activity.support.AuthenticatedApiTestConfig.class)
@SpringBootTest
@DisplayName("Cruise Activity Service Context Tests")
class CruiseActivityApplicationTest {

    @Test
    @DisplayName("Context loads successfully with H2 database")
    void contextLoads() {
        // Verifies Spring Boot context bootstrap with in-memory DB
    }
}
