package com.cruise.pos.dto;

import java.time.Instant;

public record ProvisionTerminalResponse(
        PosTerminalDto terminal,
        String rawApiKey,
        String clientCertificatePem,
        Instant certificateExpiry,
        String message
) {}
