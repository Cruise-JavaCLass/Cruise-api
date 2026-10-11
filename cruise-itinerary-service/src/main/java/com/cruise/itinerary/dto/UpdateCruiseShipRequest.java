package com.cruise.itinerary.dto;

import com.cruise.itinerary.enums.ShipStatus;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * DTO yêu cầu cập nhật thông tin tàu du lịch
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCruiseShipRequest extends BaseCruiseShipRequest {

    private ShipStatus status;
    private Boolean active;
}
