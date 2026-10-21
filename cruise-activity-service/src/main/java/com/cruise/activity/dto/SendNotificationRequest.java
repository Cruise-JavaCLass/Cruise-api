package com.cruise.activity.dto;

import com.cruise.activity.enums.NotificationCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request phát thông báo cho hành khách (KAN-363)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationRequest {

    @NotBlank(message = "Tiêu đề thông báo không được để trống")
    @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự")
    private String title;

    @NotBlank(message = "Nội dung thông báo không được để trống")
    @Size(max = 1000, message = "Nội dung tối đa 1000 ký tự")
    private String message;

    @NotNull(message = "Phân loại thông báo không được để trống")
    private NotificationCategory category;

    @Size(max = 300, message = "Đường dẫn liên kết tối đa 300 ký tự")
    private String actionUrl;

    @Size(max = 500, message = "Metadata tối đa 500 ký tự")
    private String metadata;
}
