package com.cruise.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse Unit Tests")
class ApiResponseTest {

    @Test
    @DisplayName("Should create successful response with default message")
    void shouldCreateSuccessResponseWithData() {
        String payload = "test-data";
        ApiResponse<String> response = ApiResponse.ok(payload);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Success");
        assertThat(response.getData()).isEqualTo(payload);
        assertThat(response.getTimestamp()).isNotNull().isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("Should create successful response with custom message and data")
    void shouldCreateSuccessResponseWithCustomMessage() {
        Integer payload = 42;
        String message = "Operation completed";
        ApiResponse<Integer> response = ApiResponse.ok(message, payload);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo(message);
        assertThat(response.getData()).isEqualTo(payload);
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should create error response with message and null data")
    void shouldCreateErrorResponse() {
        String errorMsg = "Resource not found";
        ApiResponse<Void> response = ApiResponse.error(errorMsg);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo(errorMsg);
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should support builder pattern and property mutators")
    void shouldSupportBuilderAndSetters() {
        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .message("Built successfully")
                .data("custom")
                .timestamp(Instant.now())
                .build();

        response.setMessage("Updated message");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Updated message");
        assertThat(response.getData()).isEqualTo("custom");
    }
}
