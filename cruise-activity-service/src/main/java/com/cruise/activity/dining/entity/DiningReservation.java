package com.cruise.activity.dining.entity;

import com.cruise.activity.dining.enums.ReservationStatus;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "dining_reservations",
    indexes = {
        @Index(name = "idx_dining_reservation_passenger_time",
               columnList = "passenger_id, reservation_time"),
        @Index(name = "idx_dining_reservation_table_time",
               columnList = "table_id, reservation_time")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_dining_reservation_code",
            columnNames = "reservation_code"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiningReservation extends BaseEntity {

    @Column(name = "reservation_code", nullable = false, length = 50)
    private String reservationCode;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "cabin_number", nullable = false, length = 30)
    private String cabinNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private DiningTable diningTable;

    @Column(name = "reservation_time", nullable = false)
    private LocalDateTime reservationTime;

    @Column(name = "guest_count", nullable = false)
    private Integer guestCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.CONFIRMED;
}
