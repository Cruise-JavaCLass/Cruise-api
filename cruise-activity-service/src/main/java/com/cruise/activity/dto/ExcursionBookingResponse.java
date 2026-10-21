package com.cruise.activity.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionBookingResponse {

    private Long bookingId;

    private Long excursionId;

    private Long passengerId;

    private String passengerName;

    private String cabinNumber;

    private String ticketCode;

    private String busNumber;

    private String groupName;

    private BigDecimal chargedAmount;

    private String transactionId;

    private Boolean isIncluded;

    private String status;

    private Instant bookedAt;
}
