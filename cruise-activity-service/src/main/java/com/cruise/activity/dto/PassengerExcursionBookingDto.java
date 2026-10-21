package com.cruise.activity.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassengerExcursionBookingDto {

    private Long bookingId;

    private Long excursionId;

    private String excursionTitle;

    private String portName;

    private LocalDate excursionDate;

    private LocalTime gatheringTime;

    private LocalTime departureTime;

    private LocalTime returnToShipTime;

    private String meetingPoint;

    private Long passengerId;

    private String passengerName;

    private String cabinNumber;

    private String ticketCode;

    private String busNumber;

    private String groupName;

    private String status;

    private BigDecimal chargedAmount;

    private Boolean isIncluded;
}
