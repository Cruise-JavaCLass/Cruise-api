package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrgentWatchdogAlertResponse {

    private String alertBatchId;
    private int targetedPassengersCount;
    private int smsSentCount;
    private int inAppSentCount;
    private boolean paBroadcastTriggered;
    private String broadcastMessage;
    private Instant dispatchedAt;
    private List<Long> contactedPassengerIds;
}
