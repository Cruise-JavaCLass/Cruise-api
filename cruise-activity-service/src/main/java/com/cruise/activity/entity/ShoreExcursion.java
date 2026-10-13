package com.cruise.activity.entity;

import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Thực thể Tour tham quan trên bờ (Shore Excursion)
 * Quản lý giá vé, sức chứa, nhà cung cấp địa phương và lịch trình (KAN-168)
 */
@Entity
@Table(name = "shore_excursions", indexes = {
        @Index(name = "idx_excursion_tour_id", columnList = "tour_id"),
        @Index(name = "idx_excursion_port_id", columnList = "port_id"),
        @Index(name = "idx_excursion_date", columnList = "excursion_date"),
        @Index(name = "idx_excursion_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShoreExcursion extends BaseEntity {

    @Column(name = "tour_id", nullable = false)
    private Long tourId;

    @Column(name = "port_id", nullable = false)
    private Long portId;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "port_name", nullable = false, length = 150)
    private String portName;

    @Column(name = "meeting_point", nullable = false, length = 200)
    private String meetingPoint;

    @Column(name = "local_provider", length = 150)
    private String localProvider;

    @Column(name = "provider_contact", length = 100)
    private String providerContact;

    @Column(name = "excursion_date", nullable = false)
    private LocalDate excursionDate;

    /**
     * Giờ tập trung của hành khách (Gathering time)
     */
    @Column(name = "gathering_time", nullable = false)
    private LocalTime gatheringTime;

    /**
     * Giờ khởi hành tour trên bờ (Departure time)
     */
    @Column(name = "departure_time", nullable = false)
    private LocalTime departureTime;

    /**
     * Giờ quay lại tàu (Return to ship time - trước All-aboard time)
     */
    @Column(name = "return_to_ship_time", nullable = false)
    private LocalTime returnToShipTime;

    /**
     * Sức chứa tối đa (Max Capacity)
     */
    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity;

    /**
     * Số lượng hành khách hiện đã đăng ký
     */
    @Column(name = "current_participants", nullable = false)
    @Builder.Default
    private Integer currentParticipants = 0;

    /**
     * Giá vé cho người lớn (Adult Price)
     */
    @Column(name = "price", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    /**
     * Giá vé cho trẻ em (Child Price)
     */
    @Column(name = "child_price", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal childPrice = BigDecimal.ZERO;

    /**
     * Loại tiền tệ (VND, USD...)
     */
    @Column(name = "currency", length = 10, nullable = false)
    @Builder.Default
    private String currency = "USD";

    /**
     * Tour nằm trong gói tour (Package Included) hay phải trả phí ngoài
     */
    @Column(name = "is_included", nullable = false)
    @Builder.Default
    private Boolean isIncluded = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ExcursionStatus status = ExcursionStatus.SCHEDULED;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}
