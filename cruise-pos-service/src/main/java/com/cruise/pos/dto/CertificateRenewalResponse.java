package com.cruise.pos.dto;

import java.time.Instant;

public record CertificateRenewalResponse(
        String deviceId,
        String clientCertificatePem,
        String certificateFingerprint,
        Instant certificateExpiry,
        String message
) {}
