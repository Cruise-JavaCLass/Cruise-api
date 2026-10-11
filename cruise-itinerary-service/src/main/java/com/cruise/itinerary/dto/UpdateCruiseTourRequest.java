package com.cruise.itinerary.dto;

import com.cruise.itinerary.enums.TourStatus;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * DTO yêu cầu cập nhật thông tin tour du thuyền
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCruiseTourRequest extends BaseCruiseTourRequest {

    private TourStatus status;
    private Boolean active;
}
