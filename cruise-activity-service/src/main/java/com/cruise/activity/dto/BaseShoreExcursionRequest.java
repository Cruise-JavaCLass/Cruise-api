package com.cruise.activity.dto;

import com.cruise.activity.enums.ExcursionStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Base DTO chứa các thuộc tính chung cho việc tạo và cập nhật Tour bờ (KAN-168).
 * Kế thừa từ ConfigureExcursionPriceCapacityScheduleRequest để tái sử dụng toàn bộ cấu hình
 * giá, sức chứa và lịch trình, loại bỏ trùng lặp mã theo yêu cầu SonarCloud Quality Gate.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseShoreExcursionRequest extends ConfigureExcursionPriceCapacityScheduleRequest {

    @NotNull(message = "Mã điểm dừng (Port ID) không được để trống")
    private Long portId;

    @NotNull(message = "Ngày thứ mấy trong tour (dayNumber) không được để trống")
    @Min(value = 1, message = "Số ngày tour phải từ 1 trở lên")
    private Integer dayNumber;

    @NotBlank(message = "Tên tour bờ không được để trống")
    private String title;

    private String description;

    @NotBlank(message = "Tên cảng ghé không được để trống")
    private String portName;

    @NotBlank(message = "Điểm tập trung không được để trống")
    private String meetingPoint;

    private String localProvider;

    private String providerContact;

    private String currency;

    private ExcursionStatus status;
}
