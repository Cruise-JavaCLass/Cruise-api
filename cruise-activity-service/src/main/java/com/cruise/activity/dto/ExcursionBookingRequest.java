package com.cruise.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionBookingRequest {

    @NotNull(message = "passengerId không được để trống")
    private Long passengerId;

    private String passengerName;

    private String cabinNumber;

    private String busNumber;

    private String groupName;

    private String emergencyPhone;

    private String specialRequirements;

    /**
     * Danh sách cabin liên kết để ưu tiên xếp chung 1 xe (AC5)
     */
    private List<String> linkedCabins;
}
