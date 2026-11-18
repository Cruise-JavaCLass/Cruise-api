package com.cruise.auth.service;

import com.cruise.auth.dto.BookedActivityItemDto;
import com.cruise.auth.dto.BoardingPassPayload;
import com.cruise.auth.dto.FolioLineItemDto;
import com.cruise.auth.dto.ItineraryStopItemDto;
import com.cruise.auth.dto.PassengerBoardingPassResponse;
import com.cruise.auth.dto.PassengerSelfActivitiesResponse;
import com.cruise.auth.dto.PassengerSelfFolioResponse;
import com.cruise.auth.dto.PassengerSelfItineraryResponse;
import com.cruise.auth.dto.PassengerSelfProfileResponse;
import com.cruise.auth.entity.User;
import com.cruise.auth.entity.UserPassengerBinding;
import com.cruise.auth.enums.VerificationStatus;
import com.cruise.auth.repository.UserPassengerBindingRepository;
import com.cruise.auth.repository.UserRepository;
import com.cruise.common.security.VerifiedCaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service xử lý các tính năng tự phục vụ của hành khách (Passenger Self-Service - KAN-721, KAN-722).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PassengerSelfServiceService {

    private final UserRepository userRepository;
    private final UserPassengerBindingRepository bindingRepository;
    private final BoardingPassCryptoService boardingPassCryptoService;

    /**
     * Xác thực và phân giải bản ghi liên kết User - Passenger hợp lệ cho caller.
     */
    public UserPassengerBinding resolveVerifiedBinding(VerifiedCaller caller) {
        if (caller == null || caller.userId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yêu cầu định danh hành khách không hợp lệ");
        }

        List<UserPassengerBinding> bindings = bindingRepository.findByUserId(caller.userId());
        return bindings.stream()
                .filter(b -> Boolean.TRUE.equals(b.getActive()) && b.getVerificationStatus() != VerificationStatus.REVOKED)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Tài khoản chưa được liên kết với hồ sơ hành khách hợp lệ"
                ));
    }

    /**
     * Lấy thông tin hồ sơ tự phục vụ của hành khách (Profile).
     */
    public PassengerSelfProfileResponse getMyProfile(VerifiedCaller caller) {
        UserPassengerBinding binding = resolveVerifiedBinding(caller);
        User user = userRepository.findById(caller.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông tin tài khoản người dùng"));

        String cabinNumber = binding.getCabinNumber() != null ? binding.getCabinNumber() : user.getCabinNumber();
        String musterStation = resolveMusterStation(cabinNumber);

        return PassengerSelfProfileResponse.builder()
                .userId(user.getId())
                .passengerId(binding.getPassengerId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .bookingReference(binding.getBookingReference())
                .cabinNumber(cabinNumber)
                .musterStation(musterStation)
                .verificationStatus(binding.getVerificationStatus())
                .rfidTag(user.getRfidTag())
                .build();
    }

    /**
     * Lấy bảng kê chi tiêu Folio tự phục vụ của hành khách.
     */
    public PassengerSelfFolioResponse getMyFolio(VerifiedCaller caller) {
        UserPassengerBinding binding = resolveVerifiedBinding(caller);

        List<FolioLineItemDto> transactions = List.of(
                FolioLineItemDto.builder()
                        .transactionId("TXN-" + binding.getPassengerId() + "-01")
                        .description("Welcome Cocktail & Dining")
                        .amount(new BigDecimal("120.00"))
                        .category("BEVERAGE_DINING")
                        .postedAt(LocalDateTime.now().minusDays(2))
                        .build(),
                FolioLineItemDto.builder()
                        .transactionId("TXN-" + binding.getPassengerId() + "-02")
                        .description("Ocean Spa Treatment")
                        .amount(new BigDecimal("185.50"))
                        .category("SPA_WELLNESS")
                        .postedAt(LocalDateTime.now().minusDays(1))
                        .build(),
                FolioLineItemDto.builder()
                        .transactionId("TXN-" + binding.getPassengerId() + "-03")
                        .description("Boutique Souvenir Shop")
                        .amount(new BigDecimal("40.00"))
                        .category("RETAIL")
                        .postedAt(LocalDateTime.now().minusHours(5))
                        .build()
        );

        BigDecimal totalCharges = new BigDecimal("345.50");
        BigDecimal creditLimit = new BigDecimal("2500.00");
        BigDecimal remainingBalance = creditLimit.subtract(totalCharges);

        return PassengerSelfFolioResponse.builder()
                .passengerId(binding.getPassengerId())
                .cabinNumber(binding.getCabinNumber())
                .bookingReference(binding.getBookingReference())
                .currency("USD")
                .creditLimit(creditLimit)
                .totalCharges(totalCharges)
                .remainingBalance(remainingBalance)
                .folioStatus("ACTIVE")
                .transactions(transactions)
                .build();
    }

    /**
     * Lấy danh sách hoạt động đã đăng ký của hành khách.
     */
    public PassengerSelfActivitiesResponse getMyActivities(VerifiedCaller caller) {
        UserPassengerBinding binding = resolveVerifiedBinding(caller);

        List<BookedActivityItemDto> activities = List.of(
                BookedActivityItemDto.builder()
                        .activityId(101L)
                        .activityName("Sunset Sea Kayaking Adventure")
                        .location("Deck 2 Watersports Marina")
                        .startTime(LocalDateTime.now().plusDays(1).withHour(16).withMinute(0))
                        .endTime(LocalDateTime.now().plusDays(1).withHour(18).withMinute(0))
                        .status("CONFIRMED")
                        .price(new BigDecimal("65.00"))
                        .build(),
                BookedActivityItemDto.builder()
                        .activityId(102L)
                        .activityName("Chef's Table Wine Pairing Masterclass")
                        .location("Deck 5 Gourmet Studio")
                        .startTime(LocalDateTime.now().plusDays(2).withHour(19).withMinute(0))
                        .endTime(LocalDateTime.now().plusDays(2).withHour(21).withMinute(30))
                        .status("CONFIRMED")
                        .price(new BigDecimal("95.00"))
                        .build()
        );

        return PassengerSelfActivitiesResponse.builder()
                .passengerId(binding.getPassengerId())
                .bookingReference(binding.getBookingReference())
                .cabinNumber(binding.getCabinNumber())
                .totalBooked(activities.size())
                .activities(activities)
                .build();
    }

    /**
     * Lấy lịch trình hải trình theo thời gian thực của hành khách.
     */
    public PassengerSelfItineraryResponse getMyItinerary(VerifiedCaller caller) {
        UserPassengerBinding binding = resolveVerifiedBinding(caller);

        List<ItineraryStopItemDto> portsOfCall = List.of(
                ItineraryStopItemDto.builder()
                        .stopOrder(1)
                        .portName("Singapore Marina Bay")
                        .country("Singapore")
                        .arrivalTime(LocalDateTime.now().minusDays(1).withHour(8).withMinute(0))
                        .departureTime(LocalDateTime.now().minusDays(1).withHour(18).withMinute(0))
                        .status("VISITED")
                        .excursionHighlights("Gardens by the Bay & Marina Bay Sands")
                        .build(),
                ItineraryStopItemDto.builder()
                        .stopOrder(2)
                        .portName("Phuket Deep Sea Port")
                        .country("Thailand")
                        .arrivalTime(LocalDateTime.now().withHour(7).withMinute(0))
                        .departureTime(LocalDateTime.now().withHour(20).withMinute(0))
                        .status("CURRENT")
                        .excursionHighlights("Phi Phi Island Speedboat & Old Town Culture")
                        .build(),
                ItineraryStopItemDto.builder()
                        .stopOrder(3)
                        .portName("Langkawi Island Harbour")
                        .country("Malaysia")
                        .arrivalTime(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0))
                        .departureTime(LocalDateTime.now().plusDays(1).withHour(19).withMinute(0))
                        .status("UPCOMING")
                        .excursionHighlights("Cable Car Sky Bridge & Mangrove Geoforest")
                        .build(),
                ItineraryStopItemDto.builder()
                        .stopOrder(4)
                        .portName("Penang Port Swettenham")
                        .country("Malaysia")
                        .arrivalTime(LocalDateTime.now().plusDays(2).withHour(8).withMinute(0))
                        .departureTime(LocalDateTime.now().plusDays(2).withHour(17).withMinute(0))
                        .status("UPCOMING")
                        .excursionHighlights("George Town Street Art & Peranakan Heritage")
                        .build()
        );

        return PassengerSelfItineraryResponse.builder()
                .passengerId(binding.getPassengerId())
                .bookingReference(binding.getBookingReference())
                .cabinNumber(binding.getCabinNumber())
                .currentStatus("CRUISING")
                .portsOfCall(portsOfCall)
                .build();
    }

    /**
     * Lấy thẻ lên tàu điện tử kèm mã QR bảo mật động của hành khách (KAN-722).
     */
    public PassengerBoardingPassResponse getMyBoardingPass(VerifiedCaller caller) {
        UserPassengerBinding binding = resolveVerifiedBinding(caller);
        String musterStation = resolveMusterStation(binding.getCabinNumber());
        String voyageCode = binding.getBookingReference();

        long validUntilTimestamp = System.currentTimeMillis() + (86400L * 1000L); // 24 giờ
        BoardingPassPayload payload = BoardingPassPayload.builder()
                .passengerId(binding.getPassengerId())
                .cabinNumber(binding.getCabinNumber())
                .musterStation(musterStation)
                .voyageCode(voyageCode)
                .validUntilTimestamp(validUntilTimestamp)
                .build();

        String qrPayload = boardingPassCryptoService.generateBoardingPassQr(payload);
        return PassengerBoardingPassResponse.builder()
                .qrPayload(qrPayload)
                .payload(payload)
                .build();
    }

    /**
     * Suy diễn vị trí trạm tập trung cứu sinh (Muster Station) dựa trên số phòng/tầng cabin.
     * Ví dụ CAB-402 -> "Muster Station A - Deck 4".
     */
    private String resolveMusterStation(String cabinNumber) {
        if (cabinNumber == null || cabinNumber.isBlank()) {
            return "Muster Station A - Deck 1";
        }
        Matcher matcher = Pattern.compile("(\\d+)").matcher(cabinNumber);
        if (matcher.find()) {
            String digits = matcher.group(1);
            String deck;
            if (digits.length() == 3) {
                deck = digits.substring(0, 1);
            } else if (digits.length() == 4) {
                if (digits.startsWith("10") || digits.startsWith("11") || digits.startsWith("12")) {
                    deck = digits.substring(0, 2);
                } else {
                    deck = digits.substring(0, 1);
                }
            } else {
                deck = String.valueOf(digits.charAt(0));
            }
            return "Muster Station A - Deck " + deck;
        }
        return "Muster Station A - Deck 1";
    }
}
