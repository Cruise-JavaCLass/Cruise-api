package com.cruise.pos.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record RenewCertificateRequest(
        Instant customExpiry,
        String csrPem
) {}
