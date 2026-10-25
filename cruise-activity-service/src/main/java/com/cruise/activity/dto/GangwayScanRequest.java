package com.cruise.activity.dto;

import com.cruise.activity.enums.GangwayMovementType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GangwayScanRequest {

    @NotNull
    private Long tourId;

    @NotNull
    private Long portCallId;

    private Long passengerId;

    private String rfidTag;

    private String qrCode;

    @NotNull
    private GangwayMovementType movementType;

    private String gangwayTerminalId;

    private String operatorName;
}
