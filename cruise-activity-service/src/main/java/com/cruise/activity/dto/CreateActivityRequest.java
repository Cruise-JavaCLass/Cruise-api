package com.cruise.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Request payload tạo mới hoạt động trên tàu
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CreateActivityRequest extends BaseActivityRequest {

    @com.fasterxml.jackson.annotation.JsonAlias({"tourId", "tour_id"})
    @Builder.Default
    private Long tourId = 1L;
}
