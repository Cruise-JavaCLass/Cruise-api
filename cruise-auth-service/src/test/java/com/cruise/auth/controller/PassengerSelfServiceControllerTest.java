package com.cruise.auth.controller;

import com.cruise.auth.entity.User;
import com.cruise.auth.entity.UserPassengerBinding;
import com.cruise.auth.enums.VerificationStatus;
import com.cruise.auth.repository.UserPassengerBindingRepository;
import com.cruise.auth.security.RbacApiTestSupport;
import com.cruise.common.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Test Suite cho PassengerSelfServiceController (KAN-721).
 * Kiểm thử toàn diện 4 endpoint tự phục vụ hành khách:
 * /profile, /folio, /activities, /itinerary.
 */
class PassengerSelfServiceControllerTest extends RbacApiTestSupport {

    @Autowired
    private UserPassengerBindingRepository bindingRepository;

    private User createPassengerUser(String username, String email, String cabinNumber) {
        return users.saveAndFlush(User.builder()
                .username(username)
                .fullName("Passenger " + username)
                .email(email)
                .role(Role.PASSENGER)
                .cabinNumber(cabinNumber)
                .rfidTag("RFID-" + username.toUpperCase())
                .active(true)
                .build());
    }

    private UserPassengerBinding createBinding(Long userId, Long passengerId, String bookingRef,
                                              String cabinNumber, VerificationStatus status, boolean active) {
        return bindingRepository.saveAndFlush(UserPassengerBinding.builder()
                .userId(userId)
                .passengerId(passengerId)
                .bookingReference(bookingRef)
                .cabinNumber(cabinNumber)
                .verificationStatus(status)
                .active(active)
                .build());
    }

    @Nested
    @DisplayName("1. Truy cập thành công 4 endpoint tự phục vụ với tài khoản PASSENGER hợp lệ")
    class SuccessfulSelfServiceTests {

        @Test
        @DisplayName("GET /profile trả về 200 OK và đúng thông tin cá nhân + muster station")
        void shouldReturnSelfProfileSuccessfully() throws Exception {
            User userA = createPassengerUser("passenger.a", "passenger.a@test.com", "CAB-402");
            createBinding(userA.getId(), 5001L, "CRUISE-2026-A", "CAB-402", VerificationStatus.VERIFIED, true);
            String token = bearer(userA);

            mvc.perform(get("/api/v1/passenger/me/profile").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.userId").value(userA.getId()))
                    .andExpect(jsonPath("$.data.passengerId").value(5001))
                    .andExpect(jsonPath("$.data.username").value("passenger.a"))
                    .andExpect(jsonPath("$.data.bookingReference").value("CRUISE-2026-A"))
                    .andExpect(jsonPath("$.data.cabinNumber").value("CAB-402"))
                    .andExpect(jsonPath("$.data.musterStation").value("Muster Station A - Deck 4"))
                    .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                    .andExpect(jsonPath("$.data.rfidTag").value("RFID-PASSENGER.A"));
        }

        @Test
        @DisplayName("GET /folio trả về 200 OK với bảng kê giao dịch gắn đúng passengerId và cabin")
        void shouldReturnSelfFolioSuccessfully() throws Exception {
            User userA = createPassengerUser("passenger.folio", "passenger.folio@test.com", "CAB-402");
            createBinding(userA.getId(), 5002L, "CRUISE-2026-FOLIO", "CAB-402", VerificationStatus.VERIFIED, true);
            String token = bearer(userA);

            mvc.perform(get("/api/v1/passenger/me/folio").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.passengerId").value(5002))
                    .andExpect(jsonPath("$.data.cabinNumber").value("CAB-402"))
                    .andExpect(jsonPath("$.data.bookingReference").value("CRUISE-2026-FOLIO"))
                    .andExpect(jsonPath("$.data.folioStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.currency").value("USD"))
                    .andExpect(jsonPath("$.data.transactions").isArray())
                    .andExpect(jsonPath("$.data.transactions", hasSize(greaterThanOrEqualTo(1))));
        }

        @Test
        @DisplayName("GET /activities trả về 200 OK với danh sách hoạt động đã đăng ký")
        void shouldReturnSelfActivitiesSuccessfully() throws Exception {
            User userA = createPassengerUser("passenger.act", "passenger.act@test.com", "CAB-402");
            createBinding(userA.getId(), 5003L, "CRUISE-2026-ACT", "CAB-402", VerificationStatus.VERIFIED, true);
            String token = bearer(userA);

            mvc.perform(get("/api/v1/passenger/me/activities").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.passengerId").value(5003))
                    .andExpect(jsonPath("$.data.bookingReference").value("CRUISE-2026-ACT"))
                    .andExpect(jsonPath("$.data.totalBooked").value(greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.data.activities").isArray());
        }

        @Test
        @DisplayName("GET /itinerary trả về 200 OK với lịch trình các cảng dừng theo thời gian thực")
        void shouldReturnSelfItinerarySuccessfully() throws Exception {
            User userA = createPassengerUser("passenger.itin", "passenger.itin@test.com", "CAB-402");
            createBinding(userA.getId(), 5004L, "CRUISE-2026-ITIN", "CAB-402", VerificationStatus.VERIFIED, true);
            String token = bearer(userA);

            mvc.perform(get("/api/v1/passenger/me/itinerary").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.passengerId").value(5004))
                    .andExpect(jsonPath("$.data.bookingReference").value("CRUISE-2026-ITIN"))
                    .andExpect(jsonPath("$.data.currentStatus").value("CRUISING"))
                    .andExpect(jsonPath("$.data.portsOfCall").isArray())
                    .andExpect(jsonPath("$.data.portsOfCall", hasSize(greaterThanOrEqualTo(1))));
        }
    }

    @Nested
    @DisplayName("2. Kiểm thử bảo mật: Chặn truy cập chưa xác thực hoặc vi phạm liên kết")
    class SecurityAndAuthorizationTests {

        @Test
        @DisplayName("Không truyền JWT token -> trả về 401 Unauthorized")
        void shouldRejectAnonymousCallsWith401() throws Exception {
            mvc.perform(get("/api/v1/passenger/me/profile")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/passenger/me/folio")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/passenger/me/activities")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/passenger/me/itinerary")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Tài khoản PASSENGER chưa có liên kết UserPassengerBinding -> trả về 403 Forbidden")
        void shouldRejectWhenUserHasNoBindingWith403() throws Exception {
            User unlinked = createPassengerUser("passenger.unlinked", "unlinked@test.com", "CAB-101");
            String token = bearer(unlinked);

            mvc.perform(get("/api/v1/passenger/me/profile").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Tài khoản PASSENGER có liên kết bị REVOKED -> trả về 403 Forbidden")
        void shouldRejectWhenBindingIsRevokedWith403() throws Exception {
            User revokedUser = createPassengerUser("passenger.revoked", "revoked@test.com", "CAB-202");
            createBinding(revokedUser.getId(), 5005L, "CRUISE-REVOKED", "CAB-202", VerificationStatus.REVOKED, true);
            String token = bearer(revokedUser);

            mvc.perform(get("/api/v1/passenger/me/profile").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Tài khoản PASSENGER có liên kết bị inactive (active=false) -> trả về 403 Forbidden")
        void shouldRejectWhenBindingIsInactiveWith403() throws Exception {
            User inactiveBindingUser = createPassengerUser("passenger.inactive", "inactive@test.com", "CAB-303");
            createBinding(inactiveBindingUser.getId(), 5006L, "CRUISE-INACTIVE", "CAB-303", VerificationStatus.VERIFIED, false);
            String token = bearer(inactiveBindingUser);

            mvc.perform(get("/api/v1/passenger/me/profile").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Cố tình gọi POST /api/v1/passenger/me/profile -> bị ServiceAccessPolicy chặn với 403 Forbidden")
        void shouldRejectMutationsWith403() throws Exception {
            User userA = createPassengerUser("passenger.post", "post@test.com", "CAB-402");
            createBinding(userA.getId(), 5007L, "CRUISE-POST", "CAB-402", VerificationStatus.VERIFIED, true);
            String token = bearer(userA);

            mvc.perform(post("/api/v1/passenger/me/profile")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("3. Kiểm tra tự động nhận diện danh tính và cách ly dữ liệu giữa các hành khách")
    class MultiPassengerIsolationTests {

        @Test
        @DisplayName("Hành khách A và B không truyền id URL nhưng nhận đúng dữ liệu riêng của chính mình")
        void shouldIsolateDataCorrectlyBetweenPassengers() throws Exception {
            // Khởi tạo Hành khách A (Deck 4)
            User userA = createPassengerUser("passenger.alpha", "alpha@test.com", "CAB-402");
            createBinding(userA.getId(), 6001L, "BOOKING-A-2026", "CAB-402", VerificationStatus.VERIFIED, true);
            String tokenA = bearer(userA);

            // Khởi tạo Hành khách B (Deck 7)
            User userB = createPassengerUser("passenger.beta", "beta@test.com", "CAB-708");
            createBinding(userB.getId(), 6002L, "BOOKING-B-2026", "CAB-708", VerificationStatus.VERIFIED, true);
            String tokenB = bearer(userB);

            // Hành khách A gọi API tự phục vụ
            mvc.perform(get("/api/v1/passenger/me/profile").header("Authorization", tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.passengerId").value(6001))
                    .andExpect(jsonPath("$.data.bookingReference").value("BOOKING-A-2026"))
                    .andExpect(jsonPath("$.data.cabinNumber").value("CAB-402"))
                    .andExpect(jsonPath("$.data.musterStation").value("Muster Station A - Deck 4"));

            mvc.perform(get("/api/v1/passenger/me/folio").header("Authorization", tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.passengerId").value(6001))
                    .andExpect(jsonPath("$.data.cabinNumber").value("CAB-402"));

            // Hành khách B gọi API tự phục vụ
            mvc.perform(get("/api/v1/passenger/me/profile").header("Authorization", tokenB))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.passengerId").value(6002))
                    .andExpect(jsonPath("$.data.bookingReference").value("BOOKING-B-2026"))
                    .andExpect(jsonPath("$.data.cabinNumber").value("CAB-708"))
                    .andExpect(jsonPath("$.data.musterStation").value("Muster Station A - Deck 7"));

            mvc.perform(get("/api/v1/passenger/me/folio").header("Authorization", tokenB))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.passengerId").value(6002))
                    .andExpect(jsonPath("$.data.cabinNumber").value("CAB-708"));
        }
    }
}
