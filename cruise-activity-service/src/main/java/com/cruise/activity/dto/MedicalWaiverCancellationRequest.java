package com.cruise.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalWaiverCancellationRequest {

    @NotBlank(message = "Lý do miễn trừ y tế/khẩn cấp không được để trống")
    @Size(min = 5, max = 500, message = "Lý do giải trình phải từ 5 đến 500 ký tự")
    private String reason;

    private String approvedByStaffId;
}
