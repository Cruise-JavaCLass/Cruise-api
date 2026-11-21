package com.cruise.activity.entity;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name="safety_muster_roster", uniqueConstraints=@UniqueConstraint(name="uk_safety_roster_incident_passenger",columnNames={"incident_id","passenger_id"}),indexes=@Index(name="idx_safety_roster_incident",columnList="incident_id, station_code"))
@Getter @Setter @NoArgsConstructor
public class MusterRosterEntry extends BaseEntity {
 @Column(name="incident_id",nullable=false) private Long incidentId;
 @Column(name="passenger_id",nullable=false) private Long passengerId;
 @Column(nullable=false,length=150) private String passengerName;
 @Column(nullable=false,length=50) private String cabinNumber;
 @Column(name="station_code",nullable=false,length=32) private String stationCode;
 @Column(length=32) private String lastObservedStation;
 @Column(length=16) private String checkinMethod;
}
