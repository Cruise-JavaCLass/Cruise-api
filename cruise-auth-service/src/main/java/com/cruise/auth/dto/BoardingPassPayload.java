package com.cruise.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload chứa thông tin thẻ lên tàu (Boarding Pass) dùng để sinh mã QR và kiểm soát ra vào (KAN-722).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardingPassPayload {

    private Long passengerId;
    private String cabinNumber;
    private String musterStation;
    private String voyageCode;
    private long validUntilTimestamp;
}
