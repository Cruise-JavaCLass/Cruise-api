package com.cruise.activity.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Request payload cập nhật toàn diện thông tin hoạt động (KAN-134)
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class UpdateActivityRequest extends BaseActivityRequest {
}
