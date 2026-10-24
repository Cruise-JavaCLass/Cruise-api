package com.cruise.itinerary.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ship_deck_zones", indexes = {
        @Index(name = "idx_deck_zone_ship", columnList = "ship_id"),
        @Index(name = "idx_deck_zone_deck", columnList = "deck_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipDeckZone extends BaseEntity {

    @Column(name = "ship_id", nullable = false)
    private Long shipId;

    @Column(name = "deck_number", nullable = false)
    private Integer deckNumber;

    @Column(name = "zone_name", nullable = false, length = 100)
    private String zoneName;

    @Column(name = "zone_code", nullable = false, length = 50)
    private String zoneCode;

    @Column(name = "max_safety_capacity", nullable = false)
    private Integer maxSafetyCapacity;

    @Column(name = "zone_type", nullable = false, length = 50)
    private String zoneType; // e.g. POOL_DECK, THEATER, DINING, CABIN_CORRIDOR, LIFEBOAT_STATION

    @Column(name = "has_lifeboat_access")
    @Builder.Default
    private Boolean hasLifeboatAccess = false;

    @Column(name = "is_restricted")
    @Builder.Default
    private Boolean isRestricted = false;
}
