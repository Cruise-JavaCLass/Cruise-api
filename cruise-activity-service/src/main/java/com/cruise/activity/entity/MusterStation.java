package com.cruise.activity.entity;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name="safety_muster_stations")
@Getter @Setter @NoArgsConstructor
public class MusterStation extends BaseEntity {
 @Column(nullable=false,unique=true,length=32) private String stationCode;
 @Column(nullable=false,length=100) private String musterLeaderStaffId;
 @Column(nullable=false,length=32) private String targetLifeboatNumber;
 @Column(nullable=false) private Integer capacity;
}
