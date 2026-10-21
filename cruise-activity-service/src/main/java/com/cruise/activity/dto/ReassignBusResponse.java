package com.cruise.activity.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReassignBusResponse {

    private Long bookingId;

    private Long excursionId;

    private String ticketCode;

    private String oldBusNumber;

    private String newBusNumber;

    private String groupName;

    private String message;
}
