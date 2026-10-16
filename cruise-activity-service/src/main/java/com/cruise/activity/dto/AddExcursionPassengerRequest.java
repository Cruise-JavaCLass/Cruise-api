package com.cruise.activity.dto;

import com.cruise.activity.enums.ExcursionPassengerStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request DTO thêm hành khách vào tour bờ (KAN-176)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddExcursionPassengerRequest {

    @NotBlank(message = "Tên hành khách không được để trống")
    private String passengerName;

    @NotBlank(message = "Số phòng cabin không được để trống")
    private String cabinNumber;

    private String ticketCode;

    private String busNumber;

    private String groupName;

    private String emergencyPhone;

    private ExcursionPassengerStatus status;

    private String specialRequirements;
}
