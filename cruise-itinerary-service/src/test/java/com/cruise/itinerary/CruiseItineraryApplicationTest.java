package com.cruise.itinerary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.context.annotation.Import(com.cruise.itinerary.support.AuthenticatedApiTestConfig.class)
@SpringBootTest
@DisplayName("Cruise Itinerary Service Context Tests")
class CruiseItineraryApplicationTest {

    @Test
    @DisplayName("Context loads successfully with H2 database")
    void contextLoads() {
        // Verifies Spring Boot context bootstrap with in-memory DB
    }
}
