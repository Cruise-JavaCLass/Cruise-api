package com.cruise.itinerary.dto;

import com.cruise.itinerary.enums.TourStatus;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * DTO yêu cầu tạo mới tour du thuyền
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCruiseTourRequest extends BaseCruiseTourRequest {

    @Builder.Default
    private TourStatus status = TourStatus.DRAFT;
}
