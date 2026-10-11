package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.BaseCruiseShipRequest;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.regex.Pattern;

/**
 * Component kiểm tra tính hợp lệ chuyên sâu của thông tin tàu du lịch (Ship Information Validation)
 */
@Component
public class ShipValidator {

    private static final Pattern SHIP_CODE_PATTERN = Pattern.compile("^[A-Z0-9_-]{3,30}$");

    /**
     * Xác thực thông tin tàu du lịch theo các quy tắc nghiệp vụ hàng hải
     */
    public void validate(BaseCruiseShipRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Thông tin tàu không được để trống");
        }

        validateCode(request.getCode());
        validateCapacityAndCabins(request.getCapacity(), request.getCrewCapacity(), request.getTotalCabins());
        validateDimensions(request.getLengthMeters(), request.getBeamMeters(), request.getDraftMeters());
        validateManufacturingYear(request.getManufacturingYear());
    }

    private void validateCode(String code) {
        if (code != null && !SHIP_CODE_PATTERN.matcher(code.trim().toUpperCase()).matches()) {
            throw new IllegalArgumentException("Mã tàu chỉ được chứa chữ cái, chữ số, dấu gạch nối và gạch dưới (từ 3-30 ký tự)");
        }
    }

    private void validateCapacityAndCabins(Integer capacity, Integer crewCapacity, Integer totalCabins) {
        if (capacity != null && totalCabins != null && totalCabins > capacity) {
            throw new IllegalArgumentException("Số lượng cabin không thể lớn hơn sức chứa hành khách tối đa");
        }

        if (capacity != null && crewCapacity != null && crewCapacity > capacity * 2) {
            throw new IllegalArgumentException("Sức chứa thủy thủ đoàn không hợp lý so với sức chứa hành khách");
        }
    }

    private void validateDimensions(Double lengthMeters, Double beamMeters, Double draftMeters) {
        if (lengthMeters != null && beamMeters != null && lengthMeters <= beamMeters) {
            throw new IllegalArgumentException("Chiều dài thân tàu phải lớn hơn chiều rộng");
        }

        if (draftMeters != null && beamMeters != null && draftMeters >= beamMeters) {
            throw new IllegalArgumentException("Mớn nước của tàu phải nhỏ hơn chiều rộng thân tàu");
        }
    }

    private void validateManufacturingYear(Integer year) {
        if (year != null) {
            int maxYear = Year.now().getValue() + 5;
            if (year > maxYear) {
                throw new IllegalArgumentException("Năm đóng tàu không hợp lệ, không được vượt quá năm " + maxYear);
            }
            if (year < 1900) {
                throw new IllegalArgumentException("Năm đóng tàu phải từ năm 1900 trở đi");
            }
        }
    }
}
