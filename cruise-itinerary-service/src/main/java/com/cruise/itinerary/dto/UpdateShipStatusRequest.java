package com.cruise.itinerary.dto;

import com.cruise.itinerary.enums.ShipStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO yêu cầu cập nhật trạng thái hoạt động của tàu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateShipStatusRequest {

    @NotNull(message = "Trạng thái tàu không được để trống")
    private ShipStatus status;
}
