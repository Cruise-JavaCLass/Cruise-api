package com.cruise.auth.security;

import com.cruise.auth.entity.User;
import com.cruise.auth.entity.UserPassengerBinding;
import com.cruise.auth.enums.VerificationStatus;
import com.cruise.auth.repository.UserPassengerBindingRepository;
import com.cruise.auth.repository.UserRepository;
import com.cruise.common.enums.Role;
import com.cruise.common.security.VerifiedCaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PassengerOwnershipValidator Unit Tests (KAN-720)")
class PassengerOwnershipValidatorTest {

    @Mock
    private UserPassengerBindingRepository bindingRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PassengerOwnershipValidator validator;

    private static final Long USER_A_ID = 101L;
    private static final Long PASSENGER_A_ID = 5001L;
    private static final Long PASSENGER_B_ID = 5002L;

    private UserPassengerBinding validBindingA;

    @BeforeEach
    void setUp() {
        validBindingA = UserPassengerBinding.builder()
                .userId(USER_A_ID)
                .passengerId(PASSENGER_A_ID)
                .bookingReference("CRUISE-2026-HALONG-001")
                .cabinNumber("CAB-402")
                .verificationStatus(VerificationStatus.VERIFIED)
                .active(true)
                .build();
    }

    @Nested
    @DisplayName("1. Kiểm tra quyền của các vai trò quản lý & nhân viên")
    class StaffRolesAuthorizationTests {

        @ParameterizedTest
        @EnumSource(value = Role.class, names = {"SYSTEM_ADMIN", "OPERATIONS_MANAGER", "FINANCE_FRONT_DESK"})
        @DisplayName("SYSTEM_ADMIN, OPERATIONS_MANAGER, FINANCE_FRONT_DESK được phép truy cập bất kỳ hồ sơ hành khách nào")
        void shouldAllowStaffRolesAccess(Role role) {
            PassengerSecurityContext auth = PassengerSecurityContext.of(999L, "staff.user", role, null);

            boolean result = validator.isOwnerOrStaff(auth, PASSENGER_A_ID);

            assertThat(result).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = Role.class, names = {"POS_STAFF", "ITINERARY_COORDINATOR", "ACTIVITY_MANAGER", "SHORE_EXCURSION_MANAGER"})
        @DisplayName("POS_STAFF, ITINERARY_COORDINATOR và các role vận hành khác bị từ chối")
        void shouldDenyNonAuthorizedStaffRoles(Role role) {
            PassengerSecurityContext auth = PassengerSecurityContext.of(888L, "ops.user", role, null);

            boolean result = validator.isOwnerOrStaff(auth, PASSENGER_A_ID);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("2. Kiểm tra quyền sở hữu của Hành khách (PASSENGER)")
    class PassengerOwnershipTests {

        @Test
        @DisplayName("Hành khách A (userId=101L) xem đúng targetPassengerId=5001L của mình -> được phép (true)")
        void shouldAllowPassengerAccessingOwnProfile() {
            PassengerSecurityContext auth = PassengerSecurityContext.of(USER_A_ID, "passenger.a", Role.PASSENGER, PASSENGER_A_ID);

            when(bindingRepository.findByUserIdAndPassengerId(USER_A_ID, PASSENGER_A_ID))
                    .thenReturn(Optional.of(validBindingA));

            boolean result = validator.isOwnerOrStaff(auth, PASSENGER_A_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Hành khách A (userId=101L) được phép khi liên kết ở trạng thái PENDING")
        void shouldAllowPassengerWhenBindingIsPending() {
            PassengerSecurityContext auth = PassengerSecurityContext.of(USER_A_ID, "passenger.a", Role.PASSENGER, PASSENGER_A_ID);

            validBindingA.setVerificationStatus(VerificationStatus.PENDING);
            when(bindingRepository.findByUserIdAndPassengerId(USER_A_ID, PASSENGER_A_ID))
                    .thenReturn(Optional.of(validBindingA));

            boolean result = validator.isOwnerOrStaff(auth, PASSENGER_A_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Hành khách A (userId=101L) cố tình xem targetPassengerId=5002L của Hành khách B -> bị chặn đứng (false)")
        void shouldDenyPassengerAccessingOtherPassengerProfile() {
            PassengerSecurityContext auth = PassengerSecurityContext.of(USER_A_ID, "passenger.a", Role.PASSENGER, PASSENGER_A_ID);

            when(bindingRepository.findByUserIdAndPassengerId(USER_A_ID, PASSENGER_B_ID))
                    .thenReturn(Optional.empty());
            when(bindingRepository.findByUserId(USER_A_ID))
                    .thenReturn(List.of(validBindingA)); // Chỉ liên kết với 5001L

            boolean result = validator.isOwnerOrStaff(auth, PASSENGER_B_ID);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Liên kết có trạng thái REVOKED -> bị chặn đứng (false)")
        void shouldDenyWhenBindingIsRevoked() {
            PassengerSecurityContext auth = PassengerSecurityContext.of(USER_A_ID, "passenger.a", Role.PASSENGER, PASSENGER_A_ID);

            validBindingA.setVerificationStatus(VerificationStatus.REVOKED);
            when(bindingRepository.findByUserIdAndPassengerId(USER_A_ID, PASSENGER_A_ID))
                    .thenReturn(Optional.of(validBindingA));

            boolean result = validator.isOwnerOrStaff(auth, PASSENGER_A_ID);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Liên kết có active=false -> bị chặn đứng (false)")
        void shouldDenyWhenBindingIsInactive() {
            PassengerSecurityContext auth = PassengerSecurityContext.of(USER_A_ID, "passenger.a", Role.PASSENGER, PASSENGER_A_ID);

            validBindingA.setActive(false);
            when(bindingRepository.findByUserIdAndPassengerId(USER_A_ID, PASSENGER_A_ID))
                    .thenReturn(Optional.of(validBindingA));

            boolean result = validator.isOwnerOrStaff(auth, PASSENGER_A_ID);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("3. Kiểm tra các tham số đầu vào không hợp lệ & Authentication")
    class InvalidParametersAndAuthTests {

        @Test
        @DisplayName("auth == null -> bị chặn (false)")
        void shouldDenyWhenAuthIsNull() {
            boolean result = validator.isOwnerOrStaff(null, PASSENGER_A_ID);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("auth chưa xác thực (!auth.isAuthenticated()) -> bị chặn (false)")
        void shouldDenyWhenNotAuthenticated() {
            PassengerSecurityContext unauthenticated = PassengerSecurityContext.builder()
                    .userId(USER_A_ID)
                    .username("passenger.a")
                    .role(Role.PASSENGER)
                    .authenticated(false)
                    .build();

            boolean result = validator.isOwnerOrStaff(unauthenticated, PASSENGER_A_ID);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("targetPassengerId == null -> trả về false")
        void shouldDenyWhenTargetPassengerIdIsNull() {
            PassengerSecurityContext auth = PassengerSecurityContext.of(USER_A_ID, "passenger.a", Role.PASSENGER, PASSENGER_A_ID);

            boolean result = validator.isOwnerOrStaff(auth, null);

            assertThat(result).isFalse();
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -100L})
        @DisplayName("targetPassengerId <= 0 -> trả về false")
        void shouldDenyWhenTargetPassengerIdIsZeroOrNegative(Long invalidId) {
            PassengerSecurityContext auth = PassengerSecurityContext.of(USER_A_ID, "passenger.a", Role.PASSENGER, PASSENGER_A_ID);

            boolean result = validator.isOwnerOrStaff(auth, invalidId);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Passenger không có userId hợp lệ -> trả về false")
        void shouldDenyWhenPassengerHasNoUserId() {
            PassengerSecurityContext auth = PassengerSecurityContext.builder()
                    .userId(null)
                    .username("passenger.a")
                    .role(Role.PASSENGER)
                    .authenticated(true)
                    .build();

            boolean result = validator.isOwnerOrStaff(auth, PASSENGER_A_ID);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("4. Kiểm tra khả năng trích xuất Identity từ các kiểu Principal khác nhau")
    class AuthenticationPrincipalExtractionTests {

        @Test
        @DisplayName("Trích xuất thành công khi principal là VerifiedCaller")
        void shouldExtractFromVerifiedCaller() {
            VerifiedCaller caller = new VerifiedCaller(USER_A_ID, "passenger.a", Role.PASSENGER);
            Authentication auth = mock(Authentication.class);
            when(auth.isAuthenticated()).thenReturn(true);
            when(auth.getPrincipal()).thenReturn(caller);

            when(bindingRepository.findByUserIdAndPassengerId(USER_A_ID, PASSENGER_A_ID))
                    .thenReturn(Optional.of(validBindingA));

            boolean result = validator.isOwnerOrStaff(auth, PASSENGER_A_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Trích xuất thành công khi principal là User entity")
        void shouldExtractFromUserEntity() {
            User user = User.builder()
                    .username("passenger.a")
                    .role(Role.PASSENGER)
                    .build();
            user.setId(USER_A_ID);

            Authentication auth = mock(Authentication.class);
            when(auth.isAuthenticated()).thenReturn(true);
            when(auth.getPrincipal()).thenReturn(user);

            when(bindingRepository.findByUserIdAndPassengerId(USER_A_ID, PASSENGER_A_ID))
                    .thenReturn(Optional.of(validBindingA));

            boolean result = validator.isOwnerOrStaff(auth, PASSENGER_A_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Trích xuất thành công khi tra cứu username qua UserRepository")
        void shouldExtractViaUserRepositoryLookup() {
            User user = User.builder()
                    .username("passenger.a")
                    .role(Role.PASSENGER)
                    .build();
            user.setId(USER_A_ID);

            Authentication auth = mock(Authentication.class);
            when(auth.isAuthenticated()).thenReturn(true);
            when(auth.getPrincipal()).thenReturn("passenger.a"); // String principal
            when(auth.getName()).thenReturn("passenger.a");

            when(userRepository.findByUsername("passenger.a")).thenReturn(Optional.of(user));
            when(bindingRepository.findByUserIdAndPassengerId(USER_A_ID, PASSENGER_A_ID))
                    .thenReturn(Optional.of(validBindingA));

            boolean result = validator.isOwnerOrStaff(auth, PASSENGER_A_ID);

            assertThat(result).isTrue();
        }
    }
}
