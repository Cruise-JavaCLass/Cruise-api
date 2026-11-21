package com.cruise.activity.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CreateActivityRequest DTO Unit Tests (KAN-700)")
class CreateActivityRequestTest {

    @Test
    @DisplayName("CreateActivityRequest default tourId is 1L and builder sets fields properly")
    void testCreateActivityRequestDefaultsAndBuilder() {
        CreateActivityRequest request = CreateActivityRequest.builder()
                .title("Captain's Welcome Gala Dinner")
                .description("Exclusive evening reception")
                .location("Grand Ballroom - Deck 7")
                .activityDate(LocalDate.of(2026, 11, 1))
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(21, 30))
                .maxCapacity(250)
                .isIncluded(true)
                .build();

        assertEquals("Captain's Welcome Gala Dinner", request.getTitle());
        assertEquals(250, request.getMaxCapacity());
        assertTrue(request.getIsIncluded());
        assertEquals(1L, request.getTourId());
        assertEquals(LocalDate.of(2026, 11, 1), request.getActivityDate());
    }

    @Test
    @DisplayName("CreateActivityRequest constructors and setters work as expected")
    void testConstructorsAndSetters() {
        CreateActivityRequest req = new CreateActivityRequest();
        req.setTourId(5L);
        req.setTitle("Sunrise Yoga");
        req.setMaxCapacity(40);
        req.setPrice(BigDecimal.ZERO);

        assertEquals(5L, req.getTourId());
        assertEquals("Sunrise Yoga", req.getTitle());
        assertEquals(40, req.getMaxCapacity());
        assertEquals(BigDecimal.ZERO, req.getPrice());
    }
}
