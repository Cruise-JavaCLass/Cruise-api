package com.cruise.auth.security;

import com.cruise.auth.entity.User;
import com.cruise.auth.entity.UserPassengerBinding;
import com.cruise.auth.enums.VerificationStatus;
import com.cruise.auth.repository.UserPassengerBindingRepository;
import com.cruise.auth.service.JwtService;
import com.cruise.common.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Security Test Suite cho KAN-723:
 * Kiểm thử phòng chống IDOR, bảo vệ quyền sở hữu hành khách, chặn leo thang đặc quyền (Privilege Escalation),
 * và xác thực tính hợp lệ của JWT token (Expired / Revoked / Anonymous).
 */
class PassengerSecurityAccessTest extends RbacApiTestSupport {

    @Autowired
    private UserPassengerBindingRepository bindingRepository;

    private User passengerA;
    private User passengerB;
    private User frontDeskStaff;
    private User posStaff;

    private String tokenA;
    private String tokenB;
    private String tokenFrontDesk;
    private String tokenPos;

    @BeforeEach
    void setUpTestData() {
        // Khách A: ID=5001, Cabin=CAB-401, Booking=CRZ-2026-A01
        passengerA = users.saveAndFlush(User.builder()
                .username("security.passenger.a")
                .fullName("Passenger Alpha")
                .email("passenger.alpha@security.test")
                .role(Role.PASSENGER)
                .cabinNumber("CAB-401")
                .active(true)
                .build());

        bindingRepository.saveAndFlush(UserPassengerBinding.builder()
                .userId(passengerA.getId())
                .passengerId(5001L)
                .cabinNumber("CAB-401")
                .bookingReference("CRZ-2026-A01")
                .verificationStatus(VerificationStatus.VERIFIED)
                .active(true)
                .build());

        // Khách B: ID=5002, Cabin=CAB-402, Booking=CRZ-2026-B02
        passengerB = users.saveAndFlush(User.builder()
                .username("security.passenger.b")
                .fullName("Passenger Beta")
                .email("passenger.beta@security.test")
                .role(Role.PASSENGER)
                .cabinNumber("CAB-402")
                .active(true)
                .build());

        bindingRepository.saveAndFlush(UserPassengerBinding.builder()
                .userId(passengerB.getId())
                .passengerId(5002L)
                .cabinNumber("CAB-402")
                .bookingReference("CRZ-2026-B02")
                .verificationStatus(VerificationStatus.VERIFIED)
                .active(true)
                .build());

        // Nhân viên lễ tân / tài chính (Được phép tra cứu)
        frontDeskStaff = account(Role.FINANCE_FRONT_DESK);

        // Nhân viên POS (Không có quyền tra cứu folio hành khách khác)
        posStaff = account(Role.POS_STAFF);

        tokenA = bearer(passengerA);
        tokenB = bearer(passengerB);
        tokenFrontDesk = bearer(frontDeskStaff);
        tokenPos = bearer(posStaff);
    }

    @Nested
    @DisplayName("1. IDOR Prevention - Chặn hành khách A truy cập dữ liệu của hành khách B")
    class IdorPreventionTests {

        @Test
        @DisplayName("Hành khách A cố tình gọi GET /passenger/5002/folio (của Khách B) -> trả về 403 Forbidden")
        void passengerACannotAccessPassengerBFolio() throws Exception {
            mvc.perform(get("/api/v1/passenger/5002/folio")
                            .header("Authorization", tokenA))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Hành khách A gọi GET /passenger/5001/folio (chính mình) -> trả về 200 OK")
        void passengerACanAccessOwnFolioById() throws Exception {
            mvc.perform(get("/api/v1/passenger/5001/folio")
                            .header("Authorization", tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.passengerId").value(5001))
                    .andExpect(jsonPath("$.data.cabinNumber").value("CAB-401"))
                    .andExpect(jsonPath("$.data.bookingReference").value("CRZ-2026-A01"));
        }
    }

    @Nested
    @DisplayName("2. Self-Service Ownership - Tự phục vụ hành khách nhận đúng dữ liệu của mình")
    class SelfServiceOwnershipTests {

        @Test
        @DisplayName("Hành khách A gọi /me/folio -> trả về 200 OK với đúng dữ liệu passengerId=5001, cabin=CAB-401")
        void passengerAGetsOwnFolioViaSelfService() throws Exception {
            mvc.perform(get("/api/v1/passenger/me/folio")
                            .header("Authorization", tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.passengerId").value(5001))
                    .andExpect(jsonPath("$.data.cabinNumber").value("CAB-401"))
                    .andExpect(jsonPath("$.data.bookingReference").value("CRZ-2026-A01"));
        }

        @Test
        @DisplayName("Hành khách B gọi /me/folio -> trả về 200 OK với đúng dữ liệu passengerId=5002, cabin=CAB-402")
        void passengerBGetsOwnFolioViaSelfService() throws Exception {
            mvc.perform(get("/api/v1/passenger/me/folio")
                            .header("Authorization", tokenB))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.passengerId").value(5002))
                    .andExpect(jsonPath("$.data.cabinNumber").value("CAB-402"))
                    .andExpect(jsonPath("$.data.bookingReference").value("CRZ-2026-B02"));
        }
    }

    @Nested
    @DisplayName("3. Front Desk Authorized Access - Lễ tân / Tài chính tra cứu hợp lệ")
    class FrontDeskAccessTests {

        @Test
        @DisplayName("FINANCE_FRONT_DESK gọi /passenger/5001/folio và /passenger/5002/folio -> cả 2 đều trả về 200 OK")
        void frontDeskCanAccessAnyPassengerFolio() throws Exception {
            // Tra cứu Khách A
            mvc.perform(get("/api/v1/passenger/5001/folio")
                            .header("Authorization", tokenFrontDesk))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.passengerId").value(5001))
                    .andExpect(jsonPath("$.data.cabinNumber").value("CAB-401"));

            // Tra cứu Khách B
            mvc.perform(get("/api/v1/passenger/5002/folio")
                            .header("Authorization", tokenFrontDesk))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.passengerId").value(5002))
                    .andExpect(jsonPath("$.data.cabinNumber").value("CAB-402"));
        }
    }

    @Nested
    @DisplayName("4. Expired & Revoked Token Rejection - Kiểm tra tính toàn vẹn phiên JWT")
    class TokenValidityTests {

        @Test
        @DisplayName("Gọi bằng token đã bị thu hồi (Revoked Token) -> trả về 401 Unauthorized")
        void revokedTokenIsRejectedWith401() throws Exception {
            String freshToken = jwt.generateAccessToken(passengerA);
            jwt.invalidateToken(freshToken);

            mvc.perform(get("/api/v1/passenger/me/folio")
                            .header("Authorization", "Bearer " + freshToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Gọi bằng token đã hết hạn (Expired Token) -> trả về 401 Unauthorized")
        void expiredTokenIsRejectedWith401() throws Exception {
            var expiredJwtService = new JwtService("cruise-secret-key-super-secure-token-2026-auth-jwt-system",
                    -1000, -1000, "cruise-auth-service");
            String expiredToken = "Bearer " + expiredJwtService.generateAccessToken(passengerA);

            mvc.perform(get("/api/v1/passenger/me/folio")
                            .header("Authorization", expiredToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Gọi không có header Authorization -> trả về 401 Unauthorized")
        void missingTokenIsRejectedWith401() throws Exception {
            mvc.perform(get("/api/v1/passenger/me/folio"))
                    .andExpect(status().isUnauthorized());

            mvc.perform(get("/api/v1/passenger/5001/folio"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("5. Privilege Escalation Prevention - Ngăn chặn leo thang đặc quyền")
    class PrivilegeEscalationTests {

        @Test
        @DisplayName("Khách A chèn header giả mạo X-Caller-Role: SYSTEM_ADMIN khi gọi /passenger/5002/folio -> vẫn bị 403")
        void passengerACannotSpoofRoleHeaderToAccessOtherPassenger() throws Exception {
            mvc.perform(get("/api/v1/passenger/5002/folio")
                            .header("Authorization", tokenA)
                            .header("X-Caller-Role", "SYSTEM_ADMIN")
                            .header("X-Caller-User-Id", "1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Khách A chèn header giả mạo X-Caller-Role: FINANCE_FRONT_DESK khi gọi /auth/roles -> bị chặn 403")
        void passengerACannotSpoofRoleHeaderToAccessAdminEndpoints() throws Exception {
            mvc.perform(get("/api/auth/roles")
                            .header("Authorization", tokenA)
                            .header("X-Caller-Role", "FINANCE_FRONT_DESK"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POS_STAFF không thuộc nhóm được ủy quyền gọi /passenger/5002/folio -> bị chặn 403")
        void posStaffCannotAccessPassengerFolio() throws Exception {
            mvc.perform(get("/api/v1/passenger/5002/folio")
                            .header("Authorization", tokenPos))
                    .andExpect(status().isForbidden());
        }
    }
}
