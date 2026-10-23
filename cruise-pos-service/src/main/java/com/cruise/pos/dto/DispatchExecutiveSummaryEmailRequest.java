package com.cruise.pos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record DispatchExecutiveSummaryEmailRequest(
        @NotBlank(message = "recipientEmail is required")
        @Email(message = "Invalid email format")
        String recipientEmail,

        String executiveNote,
        List<String> ccEmails
) {}
