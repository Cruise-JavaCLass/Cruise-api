package com.cruise.activity.dto;

import com.cruise.activity.enums.FeedbackStatus;
import jakarta.validation.constraints.NotNull;
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

    private String adminNotes;

    private String resolvedBy;
}
