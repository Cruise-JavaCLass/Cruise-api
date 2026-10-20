package com.cruise.activity.controller;

import com.cruise.activity.dto.PassengerBookingSummaryResponse;
import com.cruise.activity.dto.PassengerResponse;
import com.cruise.activity.dto.PersonalScheduleResponse;
import com.cruise.activity.dto.RegisterPassengerRequest;
import com.cruise.activity.dto.ScheduleConflictDto;
import com.cruise.activity.enums.CheckInStatus;
import com.cruise.activity.enums.RegistrationStatus;
import com.cruise.activity.service.PassengerScheduleService;
import com.cruise.activity.service.PassengerService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/activities/passengers", "/api/passengers"})
public class PassengerController {

    private final PassengerService passengerService;
    private final PassengerScheduleService passengerScheduleService;

    public PassengerController(PassengerService passengerService, PassengerScheduleService passengerScheduleService) {
        this.passengerService = passengerService;
        this.passengerScheduleService = passengerScheduleService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PassengerResponse> registerPassenger(@Valid @RequestBody RegisterPassengerRequest request) {
        return ApiResponse.ok("Đăng ký thông tin hành khách thành công", passengerService.registerPassenger(request));
    }

    @GetMapping("/tour/{tourId}")
    public ApiResponse<List<PassengerResponse>> getRegisteredPassengersByTour(
            @PathVariable Long tourId,
            @RequestParam(required = false) RegistrationStatus status
    ) {
        return ApiResponse.ok("Lấy danh sách hành khách đăng ký theo tour thành công",
                passengerService.getRegisteredPassengersByTourAndStatus(tourId, status));
    }

    @GetMapping("/tour/{tourId}/paged")
    public ApiResponse<Page<PassengerResponse>> getRegisteredPassengersByTourPaged(
            @PathVariable Long tourId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) RegistrationStatus status,
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

    /**
     * Returns booking details and an excursion summary for the requested passenger.
     *
     * @param id passenger ID from the request path
     * @return a success response containing the booking summary
     * @throws com.cruise.activity.exception.ResourceNotFoundException if the passenger is missing or inactive
     */
    @GetMapping("/{id}/summary")
    public ApiResponse<PassengerBookingSummaryResponse> getPassengerBookingSummary(@PathVariable Long id) {
        return ApiResponse.ok("Lấy thông tin tổng quan booking hành khách thành công",
                passengerService.getPassengerBookingSummary(id));
    }

    /**
     * Returns a booking summary for the query parameter's passenger ID.
     *
     * @param passengerId passenger ID, defaulting to 1 when omitted from the request
     * @return a success response containing the booking summary
     * @throws com.cruise.activity.exception.ResourceNotFoundException if the passenger is missing or inactive
     */
    @GetMapping("/summary")
    public ApiResponse<PassengerBookingSummaryResponse> getCurrentPassengerBookingSummary(
            @RequestParam(defaultValue = "1") Long passengerId
    ) {
        return ApiResponse.ok("Lấy thông tin tổng quan booking hành khách hiện tại thành công",
                passengerService.getPassengerBookingSummary(passengerId));
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

    @GetMapping(value = "/tour/{tourId}/export/csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> exportRegisteredPassengersCsv(
            @PathVariable Long tourId,
            @RequestParam(required = false) RegistrationStatus status
    ) {
        byte[] csvData = passengerService.exportRegisteredPassengersCsv(tourId, status);
        String fileName = String.format("passengers_tour_%d.csv", tourId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csvData);
    }

    @PatchMapping("/{id}/registration-status")
    public ApiResponse<PassengerResponse> updateRegistrationStatus(
            @PathVariable Long id,
            @RequestParam RegistrationStatus status
    ) {
        return ApiResponse.ok("Cập nhật trạng thái đăng ký thành công",
                passengerService.updateRegistrationStatus(id, status));
    }

    /**
     * Lấy dòng thời gian lịch trình cá nhân / cabin theo ngày của hành khách (KAN-349 / KAN-350)
     * Hỗ trợ cả 2 endpoint: /{id}/personal-schedule và /{id}/schedule
     */
    @GetMapping({"/{id}/personal-schedule", "/{id}/schedule"})
    public ApiResponse<PersonalScheduleResponse> getPersonalSchedule(
            @PathVariable Long id,
            @RequestParam(required = false) Integer day,
            @RequestParam(defaultValue = "my") String scope
    ) {
        return ApiResponse.ok("Lấy lịch trình cá nhân của hành khách thành công",
                passengerScheduleService.getPersonalSchedule(id, day, scope));
    }

    /**
     * Lấy danh sách cảnh báo xung đột lịch trình cá nhân (KAN-350)
     */
    @GetMapping("/{id}/personal-schedule/conflicts")
    public ApiResponse<List<ScheduleConflictDto>> getScheduleConflicts(
            @PathVariable Long id,
            @RequestParam(required = false) Integer day
    ) {
        return ApiResponse.ok("Lấy danh sách xung đột lịch trình thành công",
                passengerScheduleService.getScheduleConflicts(id, day));
    }
}
