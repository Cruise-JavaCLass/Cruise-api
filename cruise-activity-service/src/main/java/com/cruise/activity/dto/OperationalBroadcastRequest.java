package com.cruise.activity.dto;

import com.cruise.activity.enums.NotificationCategory;
import com.cruise.activity.enums.NotificationScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationalBroadcastRequest {

    @NotBlank(message = "Tiêu đề thông báo không được để trống")
    @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự")
    private String title;

    @NotBlank(message = "Nội dung thông báo không được để trống")
    @Size(max = 2000, message = "Nội dung tối đa 2000 ký tự")
    private String message;

    @NotNull(message = "Phân loại thông báo là bắt buộc")
    private NotificationCategory category;

    @Builder.Default
    private NotificationScope scope = NotificationScope.ALL;

    private Integer targetDeck;

    private String targetZone;

    private String actionUrl;

    private Map<String, Object> metadata;

    private Boolean playAlertSound;

    private Boolean requireAcknowledgment;
}
