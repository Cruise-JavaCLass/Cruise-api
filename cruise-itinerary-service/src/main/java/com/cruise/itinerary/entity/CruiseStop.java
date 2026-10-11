package com.cruise.itinerary.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Thực thể Điểm dừng hải trình (Cruise Stop)
 */
@Entity
@Table(name = "cruise_stops")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CruiseStop extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_id", nullable = false)
    private CruiseTour tour;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_id", nullable = false)
    private Port port;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder;

    @Column(name = "arrival_time", nullable = false)
    private LocalDateTime arrivalTime;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "all_aboard_time")
    private LocalDateTime allAboardTime;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
