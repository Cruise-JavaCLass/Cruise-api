package com.cruise.activity.entity;

import com.cruise.activity.enums.GangwayMovementType;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Nhật ký quét thẻ RFID/QR tại cổng Gangway (cầu tàu) khi ghé cảng
 */
@Entity
@Table(name = "gangway_movement_logs", indexes = {
        @Index(name = "idx_gangway_tour_id", columnList = "tour_id"),
        @Index(name = "idx_gangway_port_call_id", columnList = "port_call_id"),
        @Index(name = "idx_gangway_passenger_id", columnList = "passenger_id"),
        @Index(name = "idx_gangway_rfid_tag", columnList = "rfid_tag")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GangwayMovementLog extends BaseEntity {

    @Column(name = "tour_id", nullable = false)
    private Long tourId;

    @Column(name = "port_call_id", nullable = false)
    private Long portCallId;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "passenger_name", nullable = false, length = 150)
    private String passengerName;

    @Column(name = "cabin_number", length = 50)
    private String cabinNumber;

    @Column(name = "rfid_tag", length = 50)
    private String rfidTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private GangwayMovementType movementType;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    @Column(name = "gangway_terminal_id", length = 50)
    private String gangwayTerminalId;

    @Column(name = "operator_name", length = 100)
    private String operatorName;
}
