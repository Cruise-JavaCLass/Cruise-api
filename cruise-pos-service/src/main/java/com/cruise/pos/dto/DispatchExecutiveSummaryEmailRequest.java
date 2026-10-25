package com.cruise.pos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record DispatchExecutiveSummaryEmailRequest(
        @NotBlank(message = "recipientEmail is required")
        @Email(message = "Invalid email format")
        @Pattern(regexp = "^[^\\r\\n]+$", message = "recipientEmail cannot contain CR or LF characters")
        String recipientEmail,

        String executiveNote,
        List<@Email(message = "Invalid CC email format") @Pattern(regexp = "^[^\\r\\n]+$", message = "CC email cannot contain CR or LF characters") String> ccEmails
) {}
