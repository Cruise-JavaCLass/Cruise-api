package com.cruise.activity.dto;

import com.cruise.activity.enums.FeedbackCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class SubmitPassengerReviewRequest {

    @NotNull(message = "Passenger ID must not be null")
    private Long passengerId;

    @NotBlank(message = "Passenger name must not be blank")
    @Size(max = 150, message = "Passenger name must be at most 150 characters")
    private String passengerName;

    @NotBlank(message = "Cabin number must not be blank")
    @Size(max = 50, message = "Cabin number must be at most 50 characters")
    private String cabinNumber;

    @NotNull(message = "Category must not be null")
    private FeedbackCategory category;

    @NotNull(message = "Rating must not be null")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;

    @Size(max = 1000, message = "Comments must be at most 1000 characters")
    private String comments;

    @Size(max = 100, message = "Service outlet must be at most 100 characters")
    private String serviceOutlet;
}
