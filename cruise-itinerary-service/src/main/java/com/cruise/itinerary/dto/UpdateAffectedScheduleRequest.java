package com.cruise.itinerary.dto;

import com.cruise.itinerary.enums.ChangeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * DTO yêu cầu cập nhật lịch trình bị ảnh hưởng do thời tiết hoặc tình huống khẩn cấp (KAN-125)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAffectedScheduleRequest extends DailyScheduleDetailsDto {

    @NotNull(message = "Loại thay đổi không được để trống")
    private ChangeType changeType;

    @NotBlank(message = "Lý do thay đổi lịch trình không được để trống")
    private String reason;

    private String changedBy;
}
