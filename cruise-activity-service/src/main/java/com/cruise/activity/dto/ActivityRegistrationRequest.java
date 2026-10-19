package com.cruise.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityRegistrationRequest {

    @NotNull(message = "ID hành khách không được để trống")
    private Long passengerId;

    private String cabinNumber;

    private String notes;
}
