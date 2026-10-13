package com.cruise.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Request DTO tạo mới tour bờ (KAN-168)
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CreateShoreExcursionRequest extends BaseShoreExcursionRequest {

    @NotNull(message = "Tour ID không được để trống")
    private Long tourId;
}
