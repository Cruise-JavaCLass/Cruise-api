package com.cruise.activity.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistQueueItemDto {

    private Integer position;
    private Long registrationId;
    private Long passengerId;
    private String passengerName;
    private String cabinNumber;
    private Instant registeredAt;
    private String status;
}
