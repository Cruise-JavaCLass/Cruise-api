package com.cruise.itinerary.enums;

/**
 * Trạng thái hoạt động của tour du thuyền (Cruise Tour Status)
 */
public enum TourStatus {
    /**
     * Mới tạo, đang trong giai đoạn soạn thảo và thiết lập lộ trình
     */
    DRAFT,

    /**
     * Đã mở bán / Cho phép hành khách đặt chỗ và đăng ký dịch vụ
     */
    PUBLISHED,

    /**
     * Tour đang diễn ra trên biển (hải trình đang hoạt động)
     */
    IN_PROGRESS,

    /**
     * Tour đã kết thúc thành công toàn bộ hành trình
     */
    COMPLETED,

    /**
     * Tour đã bị hủy (do điều kiện thời tiết hoặc tình huống vận hành)
     */
    CANCELLED
}
