package com.cruise.itinerary.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.itinerary.enums.TourStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Thực thể Tour du thuyền (Cruise Tour) quản lý các hải trình du lịch nhiều ngày
 */
@Entity
@Table(name = "cruise_tours")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CruiseTour extends BaseEntity {

    /**
     * Tên tour du thuyền (ví dụ: Hải Trình Kỳ Quan Hạ Long 3 Ngày 2 Đêm)
     */
    @Column(nullable = false, unique = true, length = 150)
    private String name;

    /**
     * Mã định danh tour (ví dụ: TOUR-HL-001, TOUR-DN-3D2N)
     */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /**
     * Mô tả chi tiết tour
     */
    @Column(length = 2000)
    private String description;

    /**
     * Tàu du lịch được chỉ định phục vụ tour này
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id")
    private CruiseShip ship;

    /**
     * Thời lượng tour (số ngày)
     */
    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    /**
     * Thời lượng tour (số đêm)
     */
    @Column(name = "duration_nights", nullable = false)
    private Integer durationNights;

    /**
     * Cảng khởi hành
     */
    @Column(name = "departure_port", nullable = false, length = 150)
    private String departurePort;

    /**
     * Cảng kết thúc / cảng đích
     */
    @Column(name = "destination_port", nullable = false, length = 150)
    private String destinationPort;

    /**
     * Ngày bắt đầu tour
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * Ngày kết thúc tour
     */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Sức chứa hành khách tối đa của tour
     */
    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity;

    /**
     * Giá tour cơ bản / người
     */
    @Column(name = "base_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePrice;

    /**
     * Đơn vị tiền tệ (mặc định VND)
     */
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "VND";

    /**
     * Tóm tắt lộ trình các điểm dừng
     */
    @Column(name = "itinerary_overview", length = 2000)
    private String itineraryOverview;

    /**
     * URL hình ảnh đại diện của tour
     */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * Trạng thái của tour
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TourStatus status = TourStatus.DRAFT;

    /**
     * Trạng thái kích hoạt (hỗ trợ xóa mềm)
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Lý do hủy tour hoặc ghi chú khi thay đổi trạng thái
     */
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;
}
