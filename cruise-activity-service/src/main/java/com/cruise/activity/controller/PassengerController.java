package com.cruise.activity.controller;

import com.cruise.activity.dto.PassengerResponse;
import com.cruise.activity.dto.RegisterPassengerRequest;
import com.cruise.activity.enums.CheckInStatus;
import com.cruise.activity.service.PassengerService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activities/passengers")
public class PassengerController {

    private final PassengerService passengerService;

    public PassengerController(PassengerService passengerService) {
        this.passengerService = passengerService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PassengerResponse> registerPassenger(@Valid @RequestBody RegisterPassengerRequest request) {
        return ApiResponse.ok("Đăng ký thông tin hành khách thành công", passengerService.registerPassenger(request));
    }

    @GetMapping("/tour/{tourId}")
    public ApiResponse<List<PassengerResponse>> getRegisteredPassengersByTour(
            @PathVariable Long tourId,
            @RequestParam(required = false) com.cruise.activity.enums.RegistrationStatus status
    ) {
        return ApiResponse.ok("Lấy danh sách hành khách đăng ký theo tour thành công",
                passengerService.getRegisteredPassengersByTourAndStatus(tourId, status));
    }

    @GetMapping("/tour/{tourId}/paged")
    public ApiResponse<Page<PassengerResponse>> getRegisteredPassengersByTourPaged(
            @PathVariable Long tourId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) com.cruise.activity.enums.RegistrationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "cabinNumber") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ApiResponse.ok("Lấy danh sách hành khách phân trang thành công",
                passengerService.getRegisteredPassengersByTourPaged(tourId, keyword, status, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<PassengerResponse> getPassengerById(@PathVariable Long id) {
        return ApiResponse.ok("Lấy chi tiết hành khách thành công", passengerService.getPassengerById(id));
    }

    @GetMapping("/rfid/{rfidTag}")
    public ApiResponse<PassengerResponse> getPassengerByRfidTag(@PathVariable String rfidTag) {
        return ApiResponse.ok("Tìm thấy hành khách theo mã RFID", passengerService.getPassengerByRfidTag(rfidTag));
    }

    @PatchMapping("/{id}/check-in")
    public ApiResponse<PassengerResponse> updateCheckInStatus(
            @PathVariable Long id,
            @RequestParam CheckInStatus status
    ) {
        return ApiResponse.ok("Cập nhật trạng thái check-in thành công",
                passengerService.updateCheckInStatus(id, status));
    }

    @PatchMapping("/{id}/registration-status")
    public ApiResponse<PassengerResponse> updateRegistrationStatus(
            @PathVariable Long id,
            @RequestParam com.cruise.activity.enums.RegistrationStatus status
    ) {
        return ApiResponse.ok("Cập nhật trạng thái đăng ký thành công",
                passengerService.updateRegistrationStatus(id, status));
    }
}
