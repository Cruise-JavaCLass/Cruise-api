package com.cruise.activity.dto;

import com.cruise.activity.enums.ExcursionPassengerStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request điểm danh hành khách tham gia tour bờ (KAN-224)
 * Hỗ trợ quét vé qua ticketCode, rfidTag hoặc passengerId / cabinNumber
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcursionCheckInRequest {

    /**
     * ID hành khách trong tour (nếu xác định trực tiếp)
     */
    private Long excursionPassengerId;

    /**
     * Mã vé tour (QR Code)
     */
    private String ticketCode;

    /**
     * Mã thẻ RFID
     */
    private String rfidTag;

    /**
     * Số cabin
     */
    private String cabinNumber;

    /**
     * Trạng thái điểm danh mong muốn: CHECKED_IN, ON_TOUR, hoặc RETURNED_SAFE
     */
    @NotNull(message = "Trạng thái điểm danh không được để trống")
    private ExcursionPassengerStatus targetStatus;

    /**
     * Ghi chú bổ sung
     */
    private String note;
}
