package com.cruise.activity.enums;

/**
 * Trạng thái vòng đời của thẻ RFID / NFC trong kho và gán cho hành khách/thuyền viên
 */
public enum RfidCardStatus {
    AVAILABLE,      // Thẻ trắng sẵn sàng trong kho cấp phát
    ISSUED,         // Đã kích hoạt và gán cho hành khách / thủy thủ đoàn
    LOST_REPORTED,  // Báo mất (đã vô hiệu hóa tạm thời)
    BLACKLISTED,    // Bị khóa vĩnh viễn (đưa vào danh sách đen của các đầu đọc POS/Gangway)
    DAMAGED,        // Thẻ hỏng vật lý hoặc lỗi chip
    RETIRED         // Thu hồi sau chuyến đi
}
