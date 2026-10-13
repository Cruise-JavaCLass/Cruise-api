package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.ShoreExcursionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Bộ kiểm thử đơn vị chuyên biệt cho toàn bộ các quy tắc nghiệp vụ Tour bờ (Shore Excursion Business Rules - KAN-170).
 * Bao gồm:
 * 1. Quy tắc Lịch trình (Schedule Timing Rules: gatheringTime < departureTime < returnToShipTime)
 * 2. Quy tắc Giá vé (Pricing Rules: price >= 0, childPrice >= 0, childPrice <= price)
 * 3. Quy tắc Sức chứa (Capacity Rules: maxCapacity > 0, maxCapacity >= currentParticipants)
 * 4. Quy tắc Chống trùng lặp (Anti-Duplication Rules: unique title + port + date)
 * 5. Quy tắc Vòng đời Trạng thái (Excursion State Machine Transition Matrix)
 * 6. Quy tắc Toàn vẹn & Xóa mềm (Data Integrity & Soft Delete)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Excursion Business Rules Unit Tests (KAN-170)")
class ExcursionBusinessRulesUnitTest {

    @Mock
    private ShoreExcursionRepository excursionRepository;

    @InjectMocks
    private ShoreExcursionService excursionService;

    private ShoreExcursion existingExcursion;

    @BeforeEach
    void setUp() {
        existingExcursion = ShoreExcursion.builder()
                .tourId(1L)
                .portId(10L)
                .dayNumber(2)
                .title("Khám phá Hang Sửng Sốt & Chèo Kayak")
                .description("Tham quan hang động tự nhiên và chèo thuyền kayak")
                .portName("Cảng Quốc Tế Hạ Long")
                .meetingPoint("Cầu cảng số 2")
                .localProvider("Halong Eco Tours")
                .providerContact("+84 912 345 678")
                .excursionDate(LocalDate.of(2026, 11, 2))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(12, 0))
                .maxCapacity(50)
                .currentParticipants(20)
                .price(BigDecimal.valueOf(1200000))
                .childPrice(BigDecimal.valueOf(600000))
                .currency("VND")
                .isIncluded(false)
                .status(ExcursionStatus.SCHEDULED)
                .active(true)
                .build();
        existingExcursion.setId(100L);
    }

    @Nested
    @DisplayName("1. Quy tắc Lịch trình (Schedule Timing Rules)")
    class ScheduleTimingRules {

        @Test
        @DisplayName("validateSchedule: Từ chối khi ngày tổ chức tour bờ bị null")
        void shouldRejectNullExcursionDate() {
            assertThatThrownBy(() -> excursionService.validateSchedule(
                    null,
                    LocalTime.of(8, 0),
                    LocalTime.of(8, 30),
                    LocalTime.of(12, 0)
            )).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Ngày tổ chức tour bờ không được để trống");
        }

        @Test
        @DisplayName("validateSchedule: Từ chối khi bất kỳ mốc thời gian nào bị null")
        void shouldRejectNullTimes() {
            LocalDate date = LocalDate.of(2026, 11, 2);
            assertThatThrownBy(() -> excursionService.validateSchedule(
                    date, null, LocalTime.of(8, 30), LocalTime.of(12, 0)
            )).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Giờ tập trung, xuất phát và quay lại tàu không được để trống");

            assertThatThrownBy(() -> excursionService.validateSchedule(
                    date, LocalTime.of(8, 0), null, LocalTime.of(12, 0)
            )).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Giờ tập trung, xuất phát và quay lại tàu không được để trống");

            assertThatThrownBy(() -> excursionService.validateSchedule(
                    date, LocalTime.of(8, 0), LocalTime.of(8, 30), null
            )).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Giờ tập trung, xuất phát và quay lại tàu không được để trống");
        }

        @Test
        @DisplayName("validateSchedule: Giờ khởi hành phải sau giờ tập trung (departureTime > gatheringTime)")
        void shouldRejectDepartureTimeNotAfterGatheringTime() {
            LocalDate date = LocalDate.of(2026, 11, 2);

            // Bằng nhau
            assertThatThrownBy(() -> excursionService.validateSchedule(
                    date,
                    LocalTime.of(8, 0),
                    LocalTime.of(8, 0),
                    LocalTime.of(12, 0)
            )).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Giờ khởi hành phải sau giờ tập trung");

            // Khởi hành trước giờ tập trung
            assertThatThrownBy(() -> excursionService.validateSchedule(
                    date,
                    LocalTime.of(8, 30),
                    LocalTime.of(8, 0),
                    LocalTime.of(12, 0)
            )).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Giờ khởi hành phải sau giờ tập trung");
        }

        @Test
        @DisplayName("validateSchedule: Giờ quay lại tàu phải sau giờ khởi hành (returnToShipTime > departureTime)")
        void shouldRejectReturnTimeNotAfterDepartureTime() {
            LocalDate date = LocalDate.of(2026, 11, 2);

            // Bằng nhau
            assertThatThrownBy(() -> excursionService.validateSchedule(
                    date,
                    LocalTime.of(8, 0),
                    LocalTime.of(8, 30),
                    LocalTime.of(8, 30)
            )).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Giờ quay lại tàu phải sau giờ khởi hành");

            // Quay lại trước khi khởi hành
            assertThatThrownBy(() -> excursionService.validateSchedule(
                    date,
                    LocalTime.of(8, 0),
                    LocalTime.of(8, 30),
                    LocalTime.of(8, 15)
            )).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Giờ quay lại tàu phải sau giờ khởi hành");
        }

        @Test
        @DisplayName("validateSchedule: Chấp nhận khi gatheringTime < departureTime < returnToShipTime hợp lệ")
        void shouldAcceptValidSchedule() {
            LocalDate date = LocalDate.of(2026, 11, 2);
            excursionService.validateSchedule(
                    date,
                    LocalTime.of(7, 45),
                    LocalTime.of(8, 15),
                    LocalTime.of(12, 30)
            );
            // Không ném ngoại lệ
        }
    }

    @Nested
    @DisplayName("2. Quy tắc Giá vé (Pricing Rules)")
    class PricingRules {

        @Test
        @DisplayName("validatePriceAndCapacity: Giá vé người lớn không được null hoặc âm")
        void shouldRejectNullOrNegativeAdultPrice() {
            assertThatThrownBy(() -> excursionService.validatePriceAndCapacity(
                    null, BigDecimal.ZERO, 50
            )).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Giá vé người lớn không được để trống hoặc âm");

            assertThatThrownBy(() -> excursionService.validatePriceAndCapacity(
                    BigDecimal.valueOf(-10000), BigDecimal.ZERO, 50
            )).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Giá vé người lớn không được để trống hoặc âm");
        }

        @Test
        @DisplayName("validatePriceAndCapacity: Cho phép giá vé người lớn bằng 0 (tour miễn phí trong gói)")
        void shouldAllowZeroAdultPrice() {
            excursionService.validatePriceAndCapacity(
                    BigDecimal.ZERO, BigDecimal.ZERO, 50
            );
            // Không ném ngoại lệ
        }

        @Test
        @DisplayName("validatePriceAndCapacity: Giá vé trẻ em không được âm")
        void shouldRejectNegativeChildPrice() {
            assertThatThrownBy(() -> excursionService.validatePriceAndCapacity(
                    BigDecimal.valueOf(100000), BigDecimal.valueOf(-5000), 50
            )).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Giá vé trẻ em không được âm");
        }

        @Test
        @DisplayName("validatePriceAndCapacity: Giá vé trẻ em không được cao hơn giá vé người lớn")
        void shouldRejectChildPriceHigherThanAdultPrice() {
            assertThatThrownBy(() -> excursionService.validatePriceAndCapacity(
                    BigDecimal.valueOf(500000), BigDecimal.valueOf(500001), 50
            )).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Giá vé trẻ em không được cao hơn giá vé người lớn");
        }

        @Test
        @DisplayName("validatePriceAndCapacity: Giá vé trẻ em có thể bằng hoặc thấp hơn giá vé người lớn hoặc null")
        void shouldAcceptValidChildPrice() {
            // Giá vé trẻ em bằng người lớn
            excursionService.validatePriceAndCapacity(
                    BigDecimal.valueOf(500000), BigDecimal.valueOf(500000), 50
            );

            // Giá vé trẻ em thấp hơn
            excursionService.validatePriceAndCapacity(
                    BigDecimal.valueOf(500000), BigDecimal.valueOf(250000), 50
            );

            // Không có giá vé trẻ em (null)
            excursionService.validatePriceAndCapacity(
                    BigDecimal.valueOf(500000), null, 50
            );
        }
    }

    @Nested
    @DisplayName("3. Quy tắc Sức chứa (Capacity Rules)")
    class CapacityRules {

        @Test
        @DisplayName("validatePriceAndCapacity: Sức chứa tối đa phải lớn hơn 0")
        void shouldRejectNonPositiveMaxCapacity() {
            assertThatThrownBy(() -> excursionService.validatePriceAndCapacity(
                    BigDecimal.valueOf(500000), BigDecimal.valueOf(250000), null
            )).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Sức chứa tối đa của tour bờ phải lớn hơn 0");

            assertThatThrownBy(() -> excursionService.validatePriceAndCapacity(
                    BigDecimal.valueOf(500000), BigDecimal.valueOf(250000), 0
            )).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Sức chứa tối đa của tour bờ phải lớn hơn 0");

            assertThatThrownBy(() -> excursionService.validatePriceAndCapacity(
                    BigDecimal.valueOf(500000), BigDecimal.valueOf(250000), -5
            )).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Sức chứa tối đa của tour bờ phải lớn hơn 0");
        }

        @Test
        @DisplayName("configurePriceCapacityAndSchedule: Từ chối giảm maxCapacity nhỏ hơn số lượng đã đăng ký (currentParticipants)")
        void shouldRejectCapacityLessThanCurrentParticipantsOnConfigure() {
            // existingExcursion has currentParticipants = 20
            ConfigureExcursionPriceCapacityScheduleRequest req = ConfigureExcursionPriceCapacityScheduleRequest.builder()
                    .gatheringTime(LocalTime.of(8, 0))
                    .departureTime(LocalTime.of(8, 30))
                    .returnToShipTime(LocalTime.of(12, 0))
                    .maxCapacity(15) // < 20
                    .price(BigDecimal.valueOf(1200000))
                    .build();

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(existingExcursion));

            assertThatThrownBy(() -> excursionService.configurePriceCapacityAndSchedule(100L, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Sức chứa tối đa (15) không được nhỏ hơn số người đã đăng ký hiện tại (20)");

            verify(excursionRepository, never()).save(any());
        }

        @Test
        @DisplayName("updateExcursion: Từ chối cập nhật maxCapacity nhỏ hơn số người đã đăng ký")
        void shouldRejectCapacityLessThanCurrentParticipantsOnUpdate() {
            UpdateShoreExcursionRequest req = UpdateShoreExcursionRequest.builder()
                    .portId(10L)
                    .dayNumber(2)
                    .title("Khám phá Hang Sửng Sốt")
                    .portName("Cảng Quốc Tế Hạ Long")
                    .meetingPoint("Cầu cảng số 2")
                    .excursionDate(LocalDate.of(2026, 11, 2))
                    .gatheringTime(LocalTime.of(8, 0))
                    .departureTime(LocalTime.of(8, 30))
                    .returnToShipTime(LocalTime.of(12, 0))
                    .maxCapacity(19) // < 20
                    .price(BigDecimal.valueOf(1200000))
                    .build();

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(existingExcursion));
            when(excursionRepository.existsDuplicateExcursion(any(), any(), any(), any(), eq(100L)))
                    .thenReturn(false);

            assertThatThrownBy(() -> excursionService.updateExcursion(100L, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Sức chứa tối đa (19) không được nhỏ hơn số người đã đăng ký hiện tại (20)");
        }

        @Test
        @DisplayName("configurePriceCapacityAndSchedule: Chấp nhận khi maxCapacity >= currentParticipants")
        void shouldAcceptCapacityEqualOrGreaterThanCurrentParticipants() {
            ConfigureExcursionPriceCapacityScheduleRequest req = ConfigureExcursionPriceCapacityScheduleRequest.builder()
                    .gatheringTime(LocalTime.of(8, 0))
                    .departureTime(LocalTime.of(8, 30))
                    .returnToShipTime(LocalTime.of(12, 0))
                    .maxCapacity(20) // == 20
                    .price(BigDecimal.valueOf(1200000))
                    .build();

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(existingExcursion));
            when(excursionRepository.save(any(ShoreExcursion.class))).thenAnswer(i -> i.getArgument(0));

            var res = excursionService.configurePriceCapacityAndSchedule(100L, req);

            assertThat(res.getMaxCapacity()).isEqualTo(20);
            assertThat(res.getRemainingCapacity()).isZero();
        }
    }

    @Nested
    @DisplayName("4. Quy tắc Chống trùng lặp (Anti-Duplication Rules)")
    class AntiDuplicationRules {

        @Test
        @DisplayName("createExcursion: Từ chối tạo tour bờ khi tên tour rỗng hoặc chỉ có khoảng trắng")
        void shouldRejectBlankTitleOnCreate() {
            CreateShoreExcursionRequest req = CreateShoreExcursionRequest.builder()
                    .tourId(1L)
                    .portId(10L)
                    .dayNumber(2)
                    .title("   ")
                    .portName("Cảng Hạ Long")
                    .meetingPoint("Cầu tàu 1")
                    .excursionDate(LocalDate.of(2026, 11, 2))
                    .gatheringTime(LocalTime.of(8, 0))
                    .departureTime(LocalTime.of(8, 30))
                    .returnToShipTime(LocalTime.of(12, 0))
                    .maxCapacity(50)
                    .price(BigDecimal.valueOf(1000000))
                    .build();

            assertThatThrownBy(() -> excursionService.createExcursion(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Tên tour bờ không được để trống");
        }

        @Test
        @DisplayName("createExcursion: Từ chối khi trùng tên tour bờ tại cùng 1 cảng vào cùng 1 ngày")
        void shouldRejectDuplicateExcursionOnCreate() {
            CreateShoreExcursionRequest req = CreateShoreExcursionRequest.builder()
                    .tourId(1L)
                    .portId(10L)
                    .dayNumber(2)
                    .title("Khám phá Hang Sửng Sốt & Chèo Kayak")
                    .portName("Cảng Quốc Tế Hạ Long")
                    .meetingPoint("Cầu tàu 1")
                    .excursionDate(LocalDate.of(2026, 11, 2))
                    .gatheringTime(LocalTime.of(8, 0))
                    .departureTime(LocalTime.of(8, 30))
                    .returnToShipTime(LocalTime.of(12, 0))
                    .maxCapacity(50)
                    .price(BigDecimal.valueOf(1000000))
                    .build();

            when(excursionRepository.existsDuplicateExcursion(
                    eq(1L), eq(10L), eq(LocalDate.of(2026, 11, 2)),
                    eq("Khám phá Hang Sửng Sốt & Chèo Kayak"), isNull()
            )).thenReturn(true);

            assertThatThrownBy(() -> excursionService.createExcursion(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Tour bờ 'Khám phá Hang Sửng Sốt & Chèo Kayak' đã tồn tại tại cảng này vào ngày 2026-11-02");
        }

        @Test
        @DisplayName("updateExcursion: Cho phép giữ nguyên tên khi cập nhật bằng cách loại trừ ID hiện tại (excludeId)")
        void shouldExcludeCurrentIdWhenCheckingDuplicateOnUpdate() {
            UpdateShoreExcursionRequest req = UpdateShoreExcursionRequest.builder()
                    .portId(10L)
                    .dayNumber(2)
                    .title("Khám phá Hang Sửng Sốt & Chèo Kayak")
                    .portName("Cảng Quốc Tế Hạ Long")
                    .meetingPoint("Cầu cảng số 2 - Khu B")
                    .excursionDate(LocalDate.of(2026, 11, 2))
                    .gatheringTime(LocalTime.of(8, 0))
                    .departureTime(LocalTime.of(8, 30))
                    .returnToShipTime(LocalTime.of(12, 0))
                    .maxCapacity(60)
                    .price(BigDecimal.valueOf(1200000))
                    .build();

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(existingExcursion));
            when(excursionRepository.existsDuplicateExcursion(
                    eq(1L), eq(10L), eq(LocalDate.of(2026, 11, 2)),
                    eq("Khám phá Hang Sửng Sốt & Chèo Kayak"), eq(100L)
            )).thenReturn(false);
            when(excursionRepository.save(any(ShoreExcursion.class))).thenAnswer(i -> i.getArgument(0));

            var res = excursionService.updateExcursion(100L, req);

            assertThat(res.getMeetingPoint()).isEqualTo("Cầu cảng số 2 - Khu B");
            verify(excursionRepository).existsDuplicateExcursion(
                    eq(1L), eq(10L), eq(LocalDate.of(2026, 11, 2)),
                    eq("Khám phá Hang Sửng Sốt & Chèo Kayak"), eq(100L)
            );
        }
    }

    @Nested
    @DisplayName("5. Quy tắc Vòng đời Trạng thái Tour Bờ (State Machine Transition Rules)")
    class StateMachineRules {

        @Test
        @DisplayName("SCHEDULED cho phép chuyển sang OPEN_FOR_BOOKING, DELAYED, CANCELLED")
        void scheduledTransitions() {
            assertThat(ExcursionStatus.SCHEDULED.canTransitionTo(ExcursionStatus.OPEN_FOR_BOOKING)).isTrue();
            assertThat(ExcursionStatus.SCHEDULED.canTransitionTo(ExcursionStatus.DELAYED)).isTrue();
            assertThat(ExcursionStatus.SCHEDULED.canTransitionTo(ExcursionStatus.CANCELLED)).isTrue();
            assertThat(ExcursionStatus.SCHEDULED.canTransitionTo(ExcursionStatus.IN_PROGRESS)).isFalse();
            assertThat(ExcursionStatus.SCHEDULED.canTransitionTo(ExcursionStatus.COMPLETED)).isFalse();
            assertThat(ExcursionStatus.SCHEDULED.getAllowedTransitions())
                    .containsExactlyInAnyOrder(ExcursionStatus.OPEN_FOR_BOOKING, ExcursionStatus.DELAYED, ExcursionStatus.CANCELLED);
        }

        @Test
        @DisplayName("OPEN_FOR_BOOKING cho phép chuyển sang IN_PROGRESS, DELAYED, CANCELLED")
        void openForBookingTransitions() {
            assertThat(ExcursionStatus.OPEN_FOR_BOOKING.canTransitionTo(ExcursionStatus.IN_PROGRESS)).isTrue();
            assertThat(ExcursionStatus.OPEN_FOR_BOOKING.canTransitionTo(ExcursionStatus.DELAYED)).isTrue();
            assertThat(ExcursionStatus.OPEN_FOR_BOOKING.canTransitionTo(ExcursionStatus.CANCELLED)).isTrue();
            assertThat(ExcursionStatus.OPEN_FOR_BOOKING.canTransitionTo(ExcursionStatus.SCHEDULED)).isFalse();
            assertThat(ExcursionStatus.OPEN_FOR_BOOKING.canTransitionTo(ExcursionStatus.COMPLETED)).isFalse();
            assertThat(ExcursionStatus.OPEN_FOR_BOOKING.getAllowedTransitions())
                    .containsExactlyInAnyOrder(ExcursionStatus.IN_PROGRESS, ExcursionStatus.DELAYED, ExcursionStatus.CANCELLED);
        }

        @Test
        @DisplayName("DELAYED cho phép mở lại OPEN_FOR_BOOKING, bắt đầu IN_PROGRESS hoặc hủy CANCELLED")
        void delayedTransitions() {
            assertThat(ExcursionStatus.DELAYED.canTransitionTo(ExcursionStatus.OPEN_FOR_BOOKING)).isTrue();
            assertThat(ExcursionStatus.DELAYED.canTransitionTo(ExcursionStatus.IN_PROGRESS)).isTrue();
            assertThat(ExcursionStatus.DELAYED.canTransitionTo(ExcursionStatus.CANCELLED)).isTrue();
            assertThat(ExcursionStatus.DELAYED.canTransitionTo(ExcursionStatus.SCHEDULED)).isFalse();
            assertThat(ExcursionStatus.DELAYED.getAllowedTransitions())
                    .containsExactlyInAnyOrder(ExcursionStatus.OPEN_FOR_BOOKING, ExcursionStatus.IN_PROGRESS, ExcursionStatus.CANCELLED);
        }

        @Test
        @DisplayName("IN_PROGRESS cho phép hoàn thành COMPLETED hoặc hủy khẩn cấp CANCELLED")
        void inProgressTransitions() {
            assertThat(ExcursionStatus.IN_PROGRESS.canTransitionTo(ExcursionStatus.COMPLETED)).isTrue();
            assertThat(ExcursionStatus.IN_PROGRESS.canTransitionTo(ExcursionStatus.CANCELLED)).isTrue();
            assertThat(ExcursionStatus.IN_PROGRESS.canTransitionTo(ExcursionStatus.SCHEDULED)).isFalse();
            assertThat(ExcursionStatus.IN_PROGRESS.canTransitionTo(ExcursionStatus.OPEN_FOR_BOOKING)).isFalse();
            assertThat(ExcursionStatus.IN_PROGRESS.getAllowedTransitions())
                    .containsExactlyInAnyOrder(ExcursionStatus.COMPLETED, ExcursionStatus.CANCELLED);
        }

        @Test
        @DisplayName("COMPLETED là trạng thái kết thúc (Terminal State), không cho phép chuyển trạng thái khác")
        void completedIsTerminalState() {
            assertThat(ExcursionStatus.COMPLETED.getAllowedTransitions()).isEmpty();
            for (ExcursionStatus next : ExcursionStatus.values()) {
                if (next != ExcursionStatus.COMPLETED) {
                    assertThat(ExcursionStatus.COMPLETED.canTransitionTo(next)).isFalse();
                }
            }
        }

        @Test
        @DisplayName("CANCELLED cho phép khôi phục lại lịch SCHEDULED")
        void cancelledTransitions() {
            assertThat(ExcursionStatus.CANCELLED.canTransitionTo(ExcursionStatus.SCHEDULED)).isTrue();
            assertThat(ExcursionStatus.CANCELLED.canTransitionTo(ExcursionStatus.IN_PROGRESS)).isFalse();
            assertThat(ExcursionStatus.CANCELLED.canTransitionTo(ExcursionStatus.COMPLETED)).isFalse();
            assertThat(ExcursionStatus.CANCELLED.getAllowedTransitions())
                    .containsExactly(ExcursionStatus.SCHEDULED);
        }

        @ParameterizedTest
        @EnumSource(ExcursionStatus.class)
        @DisplayName("Mọi trạng thái đều có thể chuyển sang chính nó hoặc null")
        void selfOrNullTransitions(ExcursionStatus status) {
            assertThat(status.canTransitionTo(status)).isTrue();
            assertThat(status.canTransitionTo(null)).isTrue();
        }

        @Test
        @DisplayName("updateStatus: Từ chối chuyển đổi vi phạm ma trận trạng thái và ném BusinessRuleException")
        void shouldRejectInvalidStateTransitionInService() {
            // existingExcursion đang là SCHEDULED, không thể nhảy cóc sang COMPLETED
            UpdateExcursionStatusRequest req = UpdateExcursionStatusRequest.builder()
                    .status(ExcursionStatus.COMPLETED)
                    .build();

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(existingExcursion));

            assertThatThrownBy(() -> excursionService.updateStatus(100L, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Không thể chuyển trạng thái tour bờ từ 'SCHEDULED' sang 'COMPLETED'");

            verify(excursionRepository, never()).save(any());
        }

        @Test
        @DisplayName("updateStatus: Ghi nhận cancellationReason khi trạng thái chuyển sang CANCELLED")
        void shouldRecordCancellationReasonWhenCancelled() {
            UpdateExcursionStatusRequest req = UpdateExcursionStatusRequest.builder()
                    .status(ExcursionStatus.CANCELLED)
                    .cancellationReason("Bão cấp 9 tại vùng biển Quảng Ninh, cấm xuất bến")
                    .build();

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(existingExcursion));
            when(excursionRepository.save(any(ShoreExcursion.class))).thenAnswer(i -> i.getArgument(0));

            var res = excursionService.updateStatus(100L, req);

            assertThat(res.getStatus()).isEqualTo(ExcursionStatus.CANCELLED);
            assertThat(res.getCancellationReason()).isEqualTo("Bão cấp 9 tại vùng biển Quảng Ninh, cấm xuất bến");
        }
    }

    @Nested
    @DisplayName("6. Quy tắc Toàn vẹn & Xóa mềm (Data Integrity & Soft Delete)")
    class DataIntegrityAndSoftDeleteRules {

        @Test
        @DisplayName("deleteExcursion: Xóa mềm (active=false) và cập nhật trạng thái tour bờ sang CANCELLED")
        void shouldSoftDeleteAndCancelExcursion() {
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(existingExcursion));

            excursionService.deleteExcursion(100L);

            assertThat(existingExcursion.getActive()).isFalse();
            assertThat(existingExcursion.getStatus()).isEqualTo(ExcursionStatus.CANCELLED);
            verify(excursionRepository).save(existingExcursion);
        }

        @Test
        @DisplayName("Truy vấn tour bờ đã bị xóa mềm hoặc không tồn tại ném ResourceNotFoundException")
        void shouldRejectNotFoundOrSoftDeletedExcursion() {
            when(excursionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> excursionService.getExcursionById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy tour bờ với ID: 999");
        }
    }
}
