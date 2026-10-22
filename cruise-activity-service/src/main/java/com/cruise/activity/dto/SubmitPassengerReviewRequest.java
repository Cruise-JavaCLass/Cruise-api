package com.cruise.activity.dto;

import com.cruise.activity.enums.FeedbackCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitPassengerReviewRequest {

    @NotNull(message = "Passenger ID must not be null")
    private Long passengerId;

    @NotBlank(message = "Passenger name must not be blank")
    private String passengerName;

    @NotBlank(message = "Cabin number must not be blank")
    private String cabinNumber;

    @NotNull(message = "Category must not be null")
    private FeedbackCategory category;

    @NotNull(message = "Rating must not be null")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;

    private String comments;

    private String serviceOutlet;
}
