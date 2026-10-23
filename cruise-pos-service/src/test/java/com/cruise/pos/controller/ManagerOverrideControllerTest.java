package com.cruise.pos.controller;

import com.cruise.pos.dto.ManagerOverrideRequest;
import com.cruise.pos.dto.ManagerOverrideResponse;
import com.cruise.pos.service.ManagerOverrideService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManagerOverrideController Unit Tests")
class ManagerOverrideControllerTest {

    @Mock
    private ManagerOverrideService managerOverrideService;

    @InjectMocks
    private ManagerOverrideController controller;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("dispatchOverride: returns 201 Created when override is approved")
    void testDispatchOverrideApproved() {
        ManagerOverrideRequest req = new ManagerOverrideRequest("MGR-01", "1234", "DISCOUNT_OVERRIDE", 100L, "Loyal customer discount");
        ManagerOverrideResponse res = new ManagerOverrideResponse(true, "MGR-01", "DISCOUNT_OVERRIDE", 555L, java.time.Instant.now(), "Override approved");

        when(managerOverrideService.dispatchOverride(eq(req), eq("ANONYMOUS"))).thenReturn(res);

        ResponseEntity<ManagerOverrideResponse> response = controller.dispatchOverride(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().approved()).isTrue();
        assertThat(response.getBody().auditLogId()).isEqualTo(555L);
        verify(managerOverrideService).dispatchOverride(req, "ANONYMOUS");
    }

    @Test
    @DisplayName("dispatchOverride: returns 200 OK when override is rejected")
    void testDispatchOverrideRejected() {
        ManagerOverrideRequest req = new ManagerOverrideRequest("MGR-01", "9999", "DISCOUNT_OVERRIDE", 100L, "Invalid pin attempt");
        ManagerOverrideResponse res = new ManagerOverrideResponse(false, "MGR-01", "DISCOUNT_OVERRIDE", null, java.time.Instant.now(), "Invalid PIN");

        when(managerOverrideService.dispatchOverride(eq(req), eq("ANONYMOUS"))).thenReturn(res);

        ResponseEntity<ManagerOverrideResponse> response = controller.dispatchOverride(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().approved()).isFalse();
    }
}
