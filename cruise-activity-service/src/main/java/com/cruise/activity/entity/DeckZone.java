package com.cruise.activity.entity;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name="safety_deck_zones")
@Getter @Setter @NoArgsConstructor
public class DeckZone extends BaseEntity {
 @Column(nullable=false) private Integer deckNumber;
 @Column(nullable=false,unique=true,length=100) private String zoneCode;
 @Column(nullable=false,length=150) private String zoneName;
 @Column(nullable=false) private boolean musterStation;
 @Column(length=32) private String musterStationCode;
 @Column(nullable=false) private Integer capacity;
}
