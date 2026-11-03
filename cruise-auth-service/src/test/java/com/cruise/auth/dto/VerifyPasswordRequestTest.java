package com.cruise.auth.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VerifyPasswordRequest DTO Unit Tests")
class VerifyPasswordRequestTest {

    @Test
    @DisplayName("VerifyPasswordRequest getters, setters, builder, and constructors")
    void testDtoMethods() {
        VerifyPasswordRequest request = VerifyPasswordRequest.builder()
                .usernameOrEmail("admin")
                .password("Password123@")
                .build();

        assertEquals("admin", request.getUsernameOrEmail());
        assertEquals("Password123@", request.getPassword());

        VerifyPasswordRequest noArg = new VerifyPasswordRequest();
        noArg.setUsernameOrEmail("user@cruise.com");
        noArg.setPassword("Secret123");

        assertEquals("user@cruise.com", noArg.getUsernameOrEmail());
        assertEquals("Secret123", noArg.getPassword());

        VerifyPasswordRequest allArg = new VerifyPasswordRequest("testuser", "Pass456");
        assertEquals("testuser", allArg.getUsernameOrEmail());
        assertEquals("Pass456", allArg.getPassword());
    }
}
