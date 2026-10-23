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

    private static class AttemptRecord {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long lastFailedAt = 0L;
    }

    private final Map<String, AttemptRecord> failedAttempts = new ConcurrentHashMap<>();
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 5 * 60 * 1000L;

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

        // Only track attempts for known managers to prevent memory exhaustion from arbitrary IDs
        String expectedHash = managers.get(normalizedId);
        if (expectedHash == null) {
            return false;
        }

        AttemptRecord record = failedAttempts.computeIfAbsent(normalizedId, k -> new AttemptRecord());
        long now = System.currentTimeMillis();
        if (record.count.get() >= MAX_FAILED_ATTEMPTS) {
            if (now - record.lastFailedAt < LOCKOUT_DURATION_MS) {
                return false;
            }
            record.count.set(0);
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
                record.count.set(0); // Reset on success
                return true;
            } else {
                record.count.incrementAndGet();
                record.lastFailedAt = System.currentTimeMillis();
                return false;
            }
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }
}
