package com.cruise.activity.entity;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name="safety_ble_beacons")
@Getter @Setter @NoArgsConstructor
public class BleBeaconDevice extends BaseEntity {
 @Column(nullable=false,unique=true,length=32) private String macAddress;
 @Column(nullable=false,unique=true) private Long passengerId;
 private Integer batteryLevel;
 private java.time.Instant lastHeartbeat;
}
