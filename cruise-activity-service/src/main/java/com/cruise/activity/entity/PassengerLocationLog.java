package com.cruise.activity.entity;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name="safety_passenger_locations", indexes=@Index(name="idx_safety_location_latest",columnList="passenger_id, recorded_at DESC, id DESC"))
@Getter @Setter @NoArgsConstructor
public class PassengerLocationLog extends BaseEntity {
 @Column(name="passenger_id",nullable=false) private Long passengerId;
 @Column(nullable=false) private Integer deckNumber;
 @Column(nullable=false,length=100) private String zoneCode;
 @Column(nullable=false) private Integer rssiSignalStrength;
 @Column(name="recorded_at",nullable=false) private java.time.Instant recordedAt;
}
