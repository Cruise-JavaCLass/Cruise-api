package com.cruise.itinerary.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.itinerary.enums.ShipStatus;
import jakarta.persistence.*;
import lombok.*;

/**
 * Thực thể tàu du lịch (Cruise Ship) trong hệ thống quản lý hải trình
 */
@Entity
@Table(name = "cruise_ships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CruiseShip extends BaseEntity {

    /**
     * Tên tàu du lịch (ví dụ: Halong Elegance, Symphony of the Seas)
     */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /**
     * Mã hiệu tàu / IMO / Mã quản lý (ví dụ: SHIP-001, IMO-9823412)
     */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /**
     * Đơn vị khai thác / Hãng tàu (ví dụ: Paradise Cruises, Royal Caribbean)
     */
    @Column(nullable = false, length = 100)
    private String operator;

    /**
     * Sức chứa hành khách tối đa
     */
    @Column(nullable = false)
    private Integer capacity;

    /**
     * Sức chứa thủy thủ đoàn / nhân viên phục vụ
     */
    @Column(name = "crew_capacity", nullable = false)
    private Integer crewCapacity;

    /**
     * Tổng số cabin / phòng nghỉ trên tàu
     */
    @Column(name = "total_cabins", nullable = false)
    private Integer totalCabins;

    /**
     * Tổng số tầng / boong tàu (decks)
     */
    @Column(name = "total_decks", nullable = false)
    private Integer totalDecks;

    /**
     * Năm đóng / xuất xưởng
     */
    @Column(name = "manufacturing_year")
    private Integer manufacturingYear;

    /**
     * Chiều dài thân tàu tính bằng mét (m)
     */
    @Column(name = "length_meters")
    private Double lengthMeters;

    /**
     * Chiều rộng thân tàu tính bằng mét (m)
     */
    @Column(name = "beam_meters")
    private Double beamMeters;

    /**
     * Mớn nước tàu tính bằng mét (m)
     */
    @Column(name = "draft_meters")
    private Double draftMeters;

    /**
     * Trạng thái hoạt động hiện tại
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ShipStatus status = ShipStatus.ACTIVE;

    /**
     * Mô tả tổng quan về tàu
     */
    @Column(length = 1000)
    private String description;

    /**
     * Danh sách tiện ích trên tàu (phân tách dấu phẩy hoặc JSON text)
     */
    @Column(length = 1000)
    private String amenities;

    /**
     * Đường dẫn ảnh đại diện tàu
     */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * Trạng thái kích hoạt trong hệ thống (soft delete)
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;
}
