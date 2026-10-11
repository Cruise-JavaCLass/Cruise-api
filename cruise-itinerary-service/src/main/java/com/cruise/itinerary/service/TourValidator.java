package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.BaseCruiseTourRequest;
import com.cruise.itinerary.entity.CruiseShip;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Component kiểm tra tính hợp lệ nghiệp vụ của tour du thuyền (Tour Information Validation)
 */
@Component
public class TourValidator {

    private static final Pattern TOUR_CODE_PATTERN = Pattern.compile("^[A-Z0-9_-]{3,30}$");

    /**
     * Xác thực thông tin tour du thuyền theo các quy tắc nghiệp vụ điều phối hải trình
     */
    public void validate(BaseCruiseTourRequest request, CruiseShip ship) {
        if (request == null) {
            throw new IllegalArgumentException("Thông tin tour không được để trống");
        }

        validateCode(request.getCode());
        validateDuration(request.getDurationDays(), request.getDurationNights());
        validateDates(request.getStartDate(), request.getEndDate());
        validateCapacity(request.getMaxCapacity(), ship);
        validatePrice(request.getBasePrice());
    }

    private void validateCode(String code) {
        if (code != null && !TOUR_CODE_PATTERN.matcher(code.trim().toUpperCase()).matches()) {
            throw new IllegalArgumentException("Mã tour chỉ được chứa chữ cái, chữ số, dấu gạch nối và gạch dưới (từ 3-30 ký tự)");
        }
    }

    private void validateDuration(Integer durationDays, Integer durationNights) {
        if (durationDays != null && durationDays < 1) {
            throw new IllegalArgumentException("Số ngày tour phải từ 1 ngày trở lên");
        }

        if (durationNights != null && durationNights < 0) {
            throw new IllegalArgumentException("Số đêm tour không thể âm");
        }

        if (durationDays != null && durationNights != null && durationNights > durationDays) {
            throw new IllegalArgumentException("Số đêm tour không thể lớn hơn số ngày tour");
        }
    }

    void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Ngày kết thúc tour không thể trước ngày bắt đầu");
        }
    }

    private void validateCapacity(Integer maxCapacity, CruiseShip ship) {
        if (maxCapacity != null && maxCapacity <= 0) {
            throw new IllegalArgumentException("Sức chứa tối đa của tour phải lớn hơn 0");
        }

        if (ship != null) {
            if (Boolean.FALSE.equals(ship.getActive())) {
                throw new IllegalArgumentException("Tàu du lịch được chỉ định hiện đang ngừng hoạt động");
            }
            if (maxCapacity != null && ship.getCapacity() != null && maxCapacity > ship.getCapacity()) {
                throw new IllegalArgumentException("Sức chứa của tour (" + maxCapacity + ") không thể vượt quá sức chứa tối đa của tàu " + ship.getName() + " (" + ship.getCapacity() + ")");
            }
        }
    }

    private void validatePrice(BigDecimal basePrice) {
        if (basePrice != null && basePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Giá tour cơ bản không được âm");
        }
    }
}
