package com.cruise.activity.controller;

import com.cruise.activity.dto.EmbarkationCheckInRequest;
import com.cruise.activity.dto.EmbarkationCheckInResponse;
import com.cruise.activity.dto.PassengerEmbarkationProfileDto;
import com.cruise.activity.service.EmbarkationService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activities/passengers/embarkation")
@RequiredArgsConstructor
public class EmbarkationController {

    private final EmbarkationService embarkationService;

    @GetMapping("/lookup")
    public ApiResponse<PassengerEmbarkationProfileDto> lookupPassenger(@RequestParam String keyword) {
        return ApiResponse.ok("Xác thực thông tin hành khách thành công",
                embarkationService.lookupPassenger(keyword));
    }

    @PostMapping("/{id}/check-in")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<EmbarkationCheckInResponse> checkIn(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) EmbarkationCheckInRequest request
    ) {
        return ApiResponse.ok("Làm thủ tục lên tàu thành công",
                embarkationService.processCheckIn(id, request));
    }
}
