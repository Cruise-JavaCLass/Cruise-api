package com.cruise.activity.dto;

import com.cruise.activity.entity.RfidCardInventory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RfidInventoryDashboardDto {

    private long totalCards;
    private long availableCards;
    private long issuedCards;
    private long lostOrBlacklistedCards;
    private long damagedCards;
    private List<RfidCardInventory> recentCards;
    private List<String> activeBlacklistUids;
}
