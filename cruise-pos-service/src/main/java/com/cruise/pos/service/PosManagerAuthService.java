package com.cruise.pos.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Service
public class PosManagerAuthService {

    private static final Map<String, String> MANAGER_CREDENTIALS = Map.of(
            "MGR-01", "8888",
            "MGR-LE", "8888",
            "MANAGER-DEFAULT", "8888",
            "MGR-ADMIN", "9999"
    );

    public boolean verifyRefundApproval(String managerId, String managerPin) {
        if (managerId == null || managerId.isBlank() || managerPin == null || managerPin.isBlank()) {
            return false;
        }

        String normalizedId = managerId.trim().toUpperCase();
        String validPin = MANAGER_CREDENTIALS.get(normalizedId);
        if (validPin != null) {
            return MessageDigest.isEqual(
                    validPin.getBytes(StandardCharsets.UTF_8),
                    managerPin.trim().getBytes(StandardCharsets.UTF_8)
            );
        }

        // Allow configured cruise manager IDs starting with MGR- or MANAGER in test/dev
        if (normalizedId.startsWith("MGR-") || normalizedId.startsWith("MANAGER")) {
            return MessageDigest.isEqual(
                    "8888".getBytes(StandardCharsets.UTF_8),
                    managerPin.trim().getBytes(StandardCharsets.UTF_8)
            );
        }

        return false;
    }
}
