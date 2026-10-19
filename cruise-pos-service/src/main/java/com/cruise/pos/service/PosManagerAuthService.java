package com.cruise.pos.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@ConfigurationProperties(prefix = "pos")
public class PosManagerAuthService {

    private Map<String, String> managers = new HashMap<>(Map.of(
            "MGR-01", "2926a2731f4b312c08982cacf8061eb14bf65c1a87cc5d70e864e079c6220731",
            "MGR-LE", "2926a2731f4b312c08982cacf8061eb14bf65c1a87cc5d70e864e079c6220731",
            "MANAGER-DEFAULT", "2926a2731f4b312c08982cacf8061eb14bf65c1a87cc5d70e864e079c6220731",
            "MGR-ADMIN", "888df25ae35772424a560c7152a1de794440e0ea5cfee62828333a456a506e05"
    ));

    private final Map<String, AtomicInteger> failedAttempts = new ConcurrentHashMap<>();
    private static final int MAX_FAILED_ATTEMPTS = 5;

    public Map<String, String> getManagers() {
        return managers;
    }

    public void setManagers(Map<String, String> managers) {
        if (managers != null) {
            Map<String, String> normalized = new HashMap<>();
            managers.forEach((k, v) -> normalized.put(k.trim().toUpperCase(), v.trim()));
            this.managers = normalized;
        }
    }

    public boolean verifyRefundApproval(String managerId, String managerPin) {
        if (managerId == null || managerId.isBlank() || managerPin == null || managerPin.isBlank()) {
            return false;
        }

        String normalizedId = managerId.trim().toUpperCase();

        // Rate-limiting / brute-force protection per manager ID
        AtomicInteger attempts = failedAttempts.computeIfAbsent(normalizedId, k -> new AtomicInteger(0));
        if (attempts.get() >= MAX_FAILED_ATTEMPTS) {
            return false;
        }

        String expectedHash = managers.get(normalizedId);
        if (expectedHash == null) {
            attempts.incrementAndGet();
            return false;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] inputHash = digest.digest(managerPin.trim().getBytes(StandardCharsets.UTF_8));
            String inputHex = HexFormat.of().formatHex(inputHash);

            boolean match = MessageDigest.isEqual(
                    expectedHash.toLowerCase().getBytes(StandardCharsets.UTF_8),
                    inputHex.toLowerCase().getBytes(StandardCharsets.UTF_8)
            );

            if (match) {
                attempts.set(0); // Reset on success
                return true;
            } else {
                attempts.incrementAndGet();
                return false;
            }
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }
}
