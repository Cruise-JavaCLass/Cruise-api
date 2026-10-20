package com.cruise.pos.security;

/**
 * Danh tính đã xác thực trích từ JWT của cruise-auth-service.
 */
public record AuthPrincipal(String username, String role, String cabinNumber) {
}
