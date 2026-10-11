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
    CANCELLED;

    /**
     * Kiểm tra xem trạng thái hiện tại có thể chuyển sang trạng thái mục tiêu hay không
     */
    public boolean canTransitionTo(TourStatus target) {
        if (target == null || target == this) {
            return true;
        }
        return switch (this) {
            case DRAFT -> target == PUBLISHED || target == CANCELLED;
            case PUBLISHED -> target == IN_PROGRESS || target == DRAFT || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case COMPLETED -> false;
            case CANCELLED -> target == DRAFT;
        };
    }

    /**
     * Lấy danh sách các trạng thái được phép chuyển đến từ trạng thái hiện tại
     */
    public java.util.Set<TourStatus> getAllowedTransitions() {
        return switch (this) {
            case DRAFT -> java.util.Set.of(PUBLISHED, CANCELLED);
            case PUBLISHED -> java.util.Set.of(IN_PROGRESS, DRAFT, CANCELLED);
            case IN_PROGRESS -> java.util.Set.of(COMPLETED, CANCELLED);
            case COMPLETED -> java.util.Collections.emptySet();
            case CANCELLED -> java.util.Set.of(DRAFT);
        };
    }
}
