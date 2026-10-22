package com.cruise.activity.dto;

import com.cruise.activity.enums.ExcursionBookingStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistPromotionResultDto {

    private Long excursionId;
    private Long bookingId;
    private Long passengerId;
    private String passengerName;
    private String cabinNumber;
    private String ticketCode;
    private String assignedBus;
    private ExcursionBookingStatus status;
    private BigDecimal chargedAmount;
    private String transactionId;
    private Instant promotedAt;
    private String message;
}
