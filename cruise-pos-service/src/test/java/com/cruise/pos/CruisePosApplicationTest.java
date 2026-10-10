package com.cruise.pos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("Cruise POS Service Context Tests")
class CruisePosApplicationTest {

    @Test
    @DisplayName("Context loads successfully with H2 database")
    void contextLoads() {
        // Verifies Spring Boot context bootstrap with in-memory DB
    }
}
