package com.cruise.auth.controller;

import com.cruise.auth.dto.PassengerBoardingPassResponse;
import com.cruise.auth.dto.PassengerSelfActivitiesResponse;
import com.cruise.auth.dto.PassengerSelfFolioResponse;
import com.cruise.auth.dto.PassengerSelfItineraryResponse;
import com.cruise.auth.dto.PassengerSelfProfileResponse;
import com.cruise.auth.service.PassengerSelfServiceService;
import com.cruise.common.dto.ApiResponse;
import com.cruise.common.security.VerifiedCaller;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controller cung cấp các API tự phục vụ dành riêng cho hành khách đã đăng nhập (KAN-721).
 * Toàn bộ định danh được trích xuất an toàn từ VerifiedCaller trong HttpServletRequest do AuthorizationFilter xác thực,
 * tuyệt đối không nhận passengerId hay userId qua URL path hay query params.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/passenger/me")
@RequiredArgsConstructor
public class PassengerSelfServiceController {

    private final PassengerSelfServiceService passengerSelfService;

    private VerifiedCaller extractCaller(HttpServletRequest request) {
        Object attribute = request.getAttribute(VerifiedCaller.ATTRIBUTE);
        if (attribute instanceof VerifiedCaller caller) {
            return caller;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Yêu cầu xác thực tài khoản hành khách");
    }

    @GetMapping("/profile")
    public ApiResponse<PassengerSelfProfileResponse> getMyProfile(HttpServletRequest request) {
        VerifiedCaller caller = extractCaller(request);
        log.info("Passenger {} (userId={}) fetching self profile", caller.username(), caller.userId());
        return ApiResponse.ok(passengerSelfService.getMyProfile(caller));
    }

    @GetMapping("/folio")
    public ApiResponse<PassengerSelfFolioResponse> getMyFolio(HttpServletRequest request) {
        VerifiedCaller caller = extractCaller(request);
        log.info("Passenger {} (userId={}) fetching self folio", caller.username(), caller.userId());
        return ApiResponse.ok(passengerSelfService.getMyFolio(caller));
    }

    @GetMapping("/activities")
    public ApiResponse<PassengerSelfActivitiesResponse> getMyActivities(HttpServletRequest request) {
        VerifiedCaller caller = extractCaller(request);
        log.info("Passenger {} (userId={}) fetching self activities", caller.username(), caller.userId());
        return ApiResponse.ok(passengerSelfService.getMyActivities(caller));
    }

    @GetMapping("/itinerary")
    public ApiResponse<PassengerSelfItineraryResponse> getMyItinerary(HttpServletRequest request) {
        VerifiedCaller caller = extractCaller(request);
        log.info("Passenger {} (userId={}) fetching self itinerary", caller.username(), caller.userId());
        return ApiResponse.ok(passengerSelfService.getMyItinerary(caller));
    }

    @GetMapping("/boarding-pass")
    public ApiResponse<PassengerBoardingPassResponse> getMyBoardingPass(HttpServletRequest request) {
        VerifiedCaller caller = extractCaller(request);
        log.info("Passenger {} (userId={}) fetching self boarding pass", caller.username(), caller.userId());
        return ApiResponse.ok(passengerSelfService.getMyBoardingPass(caller));
    }
}
