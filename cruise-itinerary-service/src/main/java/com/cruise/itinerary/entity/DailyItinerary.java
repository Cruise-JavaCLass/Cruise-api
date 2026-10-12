package com.cruise.itinerary.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Thực thể Lịch trình hàng ngày (Daily Itinerary) của Tour du thuyền
 */
@Entity
@Table(name = "daily_itineraries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyItinerary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_id", nullable = false)
    private CruiseTour tour;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(name = "itinerary_date", nullable = false)
    private LocalDate itineraryDate;

    @Column(length = 200)
    private String title;

    @Column(length = 1500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_id")
    private Port port;

    @Column(name = "port_name", length = 150)
    private String portName;

    @Column(name = "arrival_time")
    private LocalTime arrivalTime;

    @Column(name = "departure_time")
    private LocalTime departureTime;

    @Column(name = "all_aboard_time")
    private LocalTime allAboardTime;

    @Column(name = "is_sea_day", nullable = false)
    @Builder.Default
    private Boolean isSeaDay = false;

    @Column(name = "weather_forecast", length = 500)
    private String weatherForecast;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
