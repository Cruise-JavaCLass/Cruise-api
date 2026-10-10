package com.cruise.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response DTO xác minh thông tin token (Token Introspection RFC 7662 compliant)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyTokenResponse {

    private boolean valid;
    private String status; // VALID, EXPIRED, REVOKED, INVALID_SIGNATURE, MALFORMED, MISSING, INVALID_TYPE
    private String message;
    private String tokenType; // ACCESS, REFRESH
    private String username;
    private Long userId;
    private String email;
    private String fullName;
    private String role;
    private String cabinNumber;
    private String rfidTag;
    private Long issuedAt;
    private Long expiresAt;
    private Long remainingSeconds;
    private String jti;
    private Map<String, Object> claims;
}
