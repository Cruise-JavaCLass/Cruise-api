package com.cruise.itinerary.dto;

import com.cruise.itinerary.enums.ShipStatus;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO yêu cầu cập nhật thông tin tàu du lịch
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCruiseShipRequest {

    @NotBlank(message = "Tên tàu không được để trống")
    @Size(min = 2, max = 100, message = "Tên tàu phải từ 2 đến 100 ký tự")
    private String name;

    @NotBlank(message = "Mã tàu không được để trống")
    @Size(min = 2, max = 50, message = "Mã tàu phải từ 2 đến 50 ký tự")
    private String code;

    @NotBlank(message = "Đơn vị vận hành không được để trống")
    @Size(max = 100, message = "Đơn vị vận hành tối đa 100 ký tự")
    private String operator;

    @NotNull(message = "Sức chứa hành khách không được để trống")
    @Min(value = 1, message = "Sức chứa hành khách phải lớn hơn 0")
    @Max(value = 20000, message = "Sức chứa hành khách không hợp lệ")
    private Integer capacity;

    @NotNull(message = "Sức chứa thủy thủ đoàn không được để trống")
    @Min(value = 1, message = "Sức chứa thủy thủ đoàn phải lớn hơn 0")
    @Max(value = 10000, message = "Sức chứa thủy thủ đoàn không hợp lệ")
    private Integer crewCapacity;

    @NotNull(message = "Tổng số cabin không được để trống")
    @Min(value = 1, message = "Tổng số cabin phải lớn hơn 0")
    private Integer totalCabins;

    @NotNull(message = "Tổng số tầng/boong không được để trống")
    @Min(value = 1, message = "Tổng số boong tàu phải lớn hơn 0")
    @Max(value = 50, message = "Tổng số boong tàu tối đa là 50")
    private Integer totalDecks;

    @Min(value = 1900, message = "Năm sản xuất phải từ năm 1900 trở đi")
    @Max(value = 2100, message = "Năm sản xuất không vượt quá 2100")
    private Integer manufacturingYear;

    @Positive(message = "Chiều dài phải là số dương")
    private Double lengthMeters;

    @Positive(message = "Chiều rộng phải là số dương")
    private Double beamMeters;

    @Positive(message = "Mớn nước phải là số dương")
    private Double draftMeters;

    private ShipStatus status;

    @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
    private String description;

    @Size(max = 1000, message = "Tiện ích tối đa 1000 ký tự")
    private String amenities;

    @Size(max = 500, message = "URL hình ảnh tối đa 500 ký tự")
    private String imageUrl;

    private Boolean active;
}
