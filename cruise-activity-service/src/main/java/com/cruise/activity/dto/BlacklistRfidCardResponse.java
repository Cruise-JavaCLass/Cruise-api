package com.cruise.activity.dto;

import com.cruise.activity.enums.RfidCardStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistRfidCardResponse {

    private String cardUid;
    private RfidCardStatus status;
    private Instant blacklistedAt;
    private String reason;
    private boolean posBlacklistBroadcasted;
    private Long affectedPassengerId;
    private String replacementCardUid;
    private String message;
}
