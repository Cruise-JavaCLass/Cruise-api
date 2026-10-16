package com.cruise.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

/**
 * Request DTO gán đoàn/nhóm và xe trung chuyển hàng loạt cho nhiều hành khách (KAN-176)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkAssignPassengerGroupRequest {

    @NotEmpty(message = "Danh sách ID hành khách không được để trống")
    private List<Long> passengerIds;

    @NotBlank(message = "Tên đoàn/nhóm không được để trống")
    private String groupName;

    private String busNumber;
}
