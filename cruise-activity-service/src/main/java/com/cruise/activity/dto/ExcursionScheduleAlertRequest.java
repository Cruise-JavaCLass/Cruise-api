package com.cruise.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionScheduleAlertRequest {

    @NotBlank(message = "Tiêu đề thông báo không được để trống")
    @Size(max = 200, message = "Tiêu đề không được vượt quá 200 ký tự")
    private String title;

    @NotBlank(message = "Nội dung cảnh báo lịch trình không được để trống")
    @Size(max = 1000, message = "Nội dung cảnh báo không được vượt quá 1000 ký tự")
    private String message;

    private String updatedMeetingLocation;

    private String updatedGatheringTime;

    private Boolean isUrgent;
}
