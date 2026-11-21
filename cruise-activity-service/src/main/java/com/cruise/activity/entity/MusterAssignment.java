package com.cruise.activity.entity;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name="safety_muster_assignments")
@Getter @Setter @NoArgsConstructor
public class MusterAssignment extends BaseEntity {
 @Column(nullable=false,unique=true) private Long passengerId;
 @Column(nullable=false,length=32) private String stationCode;
}
