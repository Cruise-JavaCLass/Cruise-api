package com.cruise.activity.dto;

import com.cruise.activity.enums.ExcursionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request DTO cập nhật trạng thái tour bờ
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateExcursionStatusRequest {

    @NotNull(message = "Trạng thái mới không được để trống")
    private ExcursionStatus status;

    private String cancellationReason;
}
