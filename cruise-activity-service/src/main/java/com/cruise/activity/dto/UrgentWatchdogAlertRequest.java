package com.cruise.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrgentWatchdogAlertRequest {

    @NotNull
    private Long tourId;

    @NotNull
    private Long portCallId;

    private List<Long> passengerIds;

    private boolean sendSms;

    private boolean sendInAppNotification;

    private boolean broadcastPaSystem;

    private String customUrgentMessage;

    private String dispatchedBy;
}
