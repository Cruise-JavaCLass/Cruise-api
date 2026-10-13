package com.cruise.activity.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Request DTO cập nhật thông tin tour bờ (KAN-168)
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class UpdateShoreExcursionRequest extends BaseShoreExcursionRequest {
}
