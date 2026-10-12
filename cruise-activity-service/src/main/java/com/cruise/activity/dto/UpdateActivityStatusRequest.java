package com.cruise.activity.dto;

import com.cruise.activity.enums.ActivityStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request payload chuyển đổi trạng thái hoạt động (KAN-136)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateActivityStatusRequest {

    @NotNull(message = "Trạng thái mục tiêu không được để trống")
    private ActivityStatus status;

    private String cancellationReason;
}
