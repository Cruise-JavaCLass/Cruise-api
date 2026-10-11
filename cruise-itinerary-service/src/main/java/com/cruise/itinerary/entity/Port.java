package com.cruise.itinerary.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Thực thể Cảng biển / Cảng du lịch (Port of Call)
 */
@Entity
@Table(name = "ports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Port extends BaseEntity {

    /**
     * Tên cảng (ví dụ: Cảng Tuần Châu, Cảng Nha Trang, Cảng Quốc Tế Hạ Long)
     */
    @Column(nullable = false, unique = true, length = 150)
    private String name;

    /**
     * Mã định danh cảng (ví dụ: VN-TCH, VN-NHA, VN-HPH)
     */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /**
     * Quốc gia
     */
    @Column(nullable = false, length = 100)
    @Builder.Default
    private String country = "Việt Nam";

    /**
     * Thành phố / Tỉnh thành
     */
    @Column(nullable = false, length = 100)
    private String city;

    /**
     * Múi giờ cảng (mặc định Asia/Ho_Chi_Minh)
     */
    @Column(length = 50)
    @Builder.Default
    private String timezone = "Asia/Ho_Chi_Minh";

    /**
     * Vĩ độ (Latitude)
     */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    /**
     * Kinh độ (Longitude)
     */
    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    /**
     * Mô tả thông tin cảng
     */
    @Column(length = 1000)
    private String description;

    /**
     * Trạng thái hoạt động
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
