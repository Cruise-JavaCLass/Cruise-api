package com.cruise.activity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request DTO gán đoàn/nhóm và xe trung chuyển cho hành khách tour bờ (KAN-176)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignPassengerGroupRequest {

    @NotBlank(message = "Tên đoàn/nhóm không được để trống")
    private String groupName;

    private String busNumber;
}
