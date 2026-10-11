package com.cruise.itinerary.dto;

import com.cruise.itinerary.enums.ShipStatus;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * DTO yêu cầu tạo mới tàu du lịch
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCruiseShipRequest extends BaseCruiseShipRequest {

    @Builder.Default
    private ShipStatus status = ShipStatus.ACTIVE;
}
