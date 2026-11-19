package com.cruise.auth.controller;

import com.cruise.auth.dto.PassengerBoardingPassResponse;
import com.cruise.auth.dto.PassengerSelfActivitiesResponse;
import com.cruise.auth.dto.PassengerSelfFolioResponse;
import com.cruise.auth.dto.PassengerSelfItineraryResponse;
import com.cruise.auth.dto.PassengerSelfProfileResponse;
import com.cruise.auth.security.PassengerOwnershipValidator;
import com.cruise.auth.security.PassengerSecurityContext;
import com.cruise.auth.service.PassengerSelfServiceService;
import com.cruise.common.dto.ApiResponse;
import com.cruise.common.security.VerifiedCaller;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controller cung cấp các API tự phục vụ và tra cứu tài khoản hành khách (KAN-721, KAN-722, KAN-723).
 * Toàn bộ định danh được trích xuất an toàn từ VerifiedCaller trong HttpServletRequest do AuthorizationFilter xác thực.
 * Endpoint /{passengerId}/folio bắt buộc phải thông qua PassengerOwnershipValidator để ngăn chặn lỗ hổng IDOR.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/passenger")
@RequiredArgsConstructor
public class PassengerSelfServiceController {

    private final PassengerSelfServiceService passengerSelfService;
    private final PassengerOwnershipValidator passengerOwnershipValidator;

    private VerifiedCaller extractCaller(HttpServletRequest request) {
        Object attribute = request.getAttribute(VerifiedCaller.ATTRIBUTE);
        if (attribute instanceof VerifiedCaller caller) {
            return caller;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Yêu cầu xác thực tài khoản hành khách");
    }

    @GetMapping("/me/profile")
    public ApiResponse<PassengerSelfProfileResponse> getMyProfile(HttpServletRequest request) {
        VerifiedCaller caller = extractCaller(request);
        log.info("Passenger {} (userId={}) fetching self profile", caller.username(), caller.userId());
        return ApiResponse.ok(passengerSelfService.getMyProfile(caller));
    }

    @GetMapping("/me/folio")
    public ApiResponse<PassengerSelfFolioResponse> getMyFolio(HttpServletRequest request) {
        VerifiedCaller caller = extractCaller(request);
        log.info("Passenger {} (userId={}) fetching self folio", caller.username(), caller.userId());
        return ApiResponse.ok(passengerSelfService.getMyFolio(caller));
    }

    @GetMapping("/me/activities")
    public ApiResponse<PassengerSelfActivitiesResponse> getMyActivities(HttpServletRequest request) {
        VerifiedCaller caller = extractCaller(request);
        log.info("Passenger {} (userId={}) fetching self activities", caller.username(), caller.userId());
        return ApiResponse.ok(passengerSelfService.getMyActivities(caller));
    }

    @GetMapping("/me/itinerary")
    public ApiResponse<PassengerSelfItineraryResponse> getMyItinerary(HttpServletRequest request) {
        VerifiedCaller caller = extractCaller(request);
        log.info("Passenger {} (userId={}) fetching self itinerary", caller.username(), caller.userId());
        return ApiResponse.ok(passengerSelfService.getMyItinerary(caller));
    }

    @GetMapping("/me/boarding-pass")
    public ApiResponse<PassengerBoardingPassResponse> getMyBoardingPass(HttpServletRequest request) {
        VerifiedCaller caller = extractCaller(request);
        log.info("Passenger {} (userId={}) fetching self boarding pass", caller.username(), caller.userId());
        return ApiResponse.ok(passengerSelfService.getMyBoardingPass(caller));
    }

    @GetMapping("/{passengerId}/folio")
    public ApiResponse<PassengerSelfFolioResponse> getPassengerFolio(
            @PathVariable("passengerId") Long passengerId,
            HttpServletRequest request
    ) {
        VerifiedCaller caller = extractCaller(request);
        PassengerSecurityContext authContext = PassengerSecurityContext.from(caller, passengerId);
        boolean allowed = passengerOwnershipValidator.isOwnerOrStaff(authContext, passengerId);
        if (!allowed) {
            log.warn("Access denied: caller {} (role={}, userId={}) attempted to access folio for passengerId={}",
                    caller.username(), caller.role(), caller.userId(), passengerId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền truy cập folio của hành khách này");
        }
        log.info("Caller {} (role={}, userId={}) granted access to folio for passengerId={}",
                caller.username(), caller.role(), caller.userId(), passengerId);
        return ApiResponse.ok(passengerSelfService.getFolioByPassengerId(passengerId));
    }
}
