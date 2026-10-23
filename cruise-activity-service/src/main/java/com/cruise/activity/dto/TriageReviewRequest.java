package com.cruise.activity.dto;

import com.cruise.activity.enums.FeedbackStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageReviewRequest {

    @NotNull(message = "Status must not be null")
    private FeedbackStatus status;

    @Size(max = 1000, message = "Admin notes must be at most 1000 characters")
    private String adminNotes;

    @Size(max = 100, message = "Resolved by must be at most 100 characters")
    private String resolvedBy;
}
