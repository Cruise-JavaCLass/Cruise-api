package com.cruise.activity.service;

import com.cruise.activity.dto.UpdateExcursionStatusRequest;
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
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.mockito.Mockito.*;

/**
 * Unit test suite chuyên biệt cho chuyển đổi trạng thái và theo dõi lịch trình tour bờ.
 * Đáp ứng các tiêu chí kiểm thử cho subtasks:
 * - KAN-187: Define Excursion Status Transitions
 * - KAN-188: Update Delayed, Cancelled and Completed Statuses
 * - KAN-189: Track Gathering and Return-to-Ship Times
 * - KAN-190: Write Unit Tests for Status Transitions
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Excursion Status Transitions & Schedule Tracking Unit Tests (KAN-187, KAN-188, KAN-189, KAN-190)")
class ExcursionStatusTransitionsUnitTest {

    @Mock
    private ShoreExcursionRepository excursionRepository;

    @InjectMocks
    private ShoreExcursionService excursionService;

    private ShoreExcursion excursion;

    @BeforeEach
    void setUp() {
        excursion = ShoreExcursion.builder()
                .tourId(10L)
                .portId(20L)
                .dayNumber(2)
                .title("Tour Thăm Vịnh Hạ Long")
                .description("Du ngoạn vịnh di sản và tắm biển Ti Tốp")
                .portName("Cảng Quốc Tế Tuần Châu")
                .meetingPoint("Cầu tàu số 1")
                .excursionDate(LocalDate.of(2026, 11, 20))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(12, 30))
                .maxCapacity(50)
                .currentParticipants(15)
                .price(BigDecimal.valueOf(1500000))
                .childPrice(BigDecimal.valueOf(750000))
                .currency("VND")
                .status(ExcursionStatus.SCHEDULED)
                .active(true)
                .build();
        excursion.setId(200L);
    }

    @Nested
    @DisplayName("KAN-187: Định nghĩa và kiểm tra các trạng thái hợp lệ (Allowed Transitions)")
    class StateMachineDefinitionTests {

        @Test
        @DisplayName("getAllowedStatusTransitions: Trả về tập hợp trạng thái hợp lệ theo từng bước vòng đời")
        void shouldReturnAllowedTransitionsForExcursion() {
            when(excursionRepository.findById(200L)).thenReturn(Optional.of(excursion));

            Set<ExcursionStatus> allowedFromScheduled = excursionService.getAllowedStatusTransitions(200L);
            assertThat(allowedFromScheduled).containsExactlyInAnyOrder(
                    ExcursionStatus.OPEN_FOR_BOOKING,
                    ExcursionStatus.DELAYED,
                    ExcursionStatus.CANCELLED
            );

            excursion.setStatus(ExcursionStatus.OPEN_FOR_BOOKING);
            Set<ExcursionStatus> allowedFromOpen = excursionService.getAllowedStatusTransitions(200L);
            assertThat(allowedFromOpen).containsExactlyInAnyOrder(
                    ExcursionStatus.IN_PROGRESS,
                    ExcursionStatus.DELAYED,
                    ExcursionStatus.CANCELLED
            );

            excursion.setStatus(ExcursionStatus.IN_PROGRESS);
            Set<ExcursionStatus> allowedFromInProgress = excursionService.getAllowedStatusTransitions(200L);
            assertThat(allowedFromInProgress).containsExactlyInAnyOrder(
                    ExcursionStatus.COMPLETED,
                    ExcursionStatus.CANCELLED
            );

            excursion.setStatus(ExcursionStatus.COMPLETED);
            Set<ExcursionStatus> allowedFromCompleted = excursionService.getAllowedStatusTransitions(200L);
            assertThat(allowedFromCompleted).isEmpty();
        }

        @ParameterizedTest(name = "Từ {0} chuyển sang {1} phải hợp lệ")
        @CsvSource({
                "SCHEDULED, OPEN_FOR_BOOKING",
                "SCHEDULED, DELAYED",
                "SCHEDULED, CANCELLED",
                "OPEN_FOR_BOOKING, IN_PROGRESS",
                "OPEN_FOR_BOOKING, DELAYED",
                "OPEN_FOR_BOOKING, CANCELLED",
                "DELAYED, OPEN_FOR_BOOKING",
                "DELAYED, IN_PROGRESS",
                "DELAYED, CANCELLED",
                "IN_PROGRESS, COMPLETED",
                "IN_PROGRESS, CANCELLED",
                "CANCELLED, SCHEDULED"
        })
        void shouldValidateAllAllowedTransitions(ExcursionStatus from, ExcursionStatus to) {
            assertThat(from.canTransitionTo(to)).isTrue();
        }

        @ParameterizedTest(name = "Từ {0} chuyển sang {1} phải bị từ chối")
        @CsvSource({
                "SCHEDULED, IN_PROGRESS",
                "SCHEDULED, COMPLETED",
                "OPEN_FOR_BOOKING, COMPLETED",
                "OPEN_FOR_BOOKING, SCHEDULED",
                "COMPLETED, SCHEDULED",
                "COMPLETED, OPEN_FOR_BOOKING",
                "COMPLETED, IN_PROGRESS",
                "COMPLETED, DELAYED",
                "COMPLETED, CANCELLED",
                "CANCELLED, IN_PROGRESS",
                "CANCELLED, COMPLETED"
        })
        void shouldRejectDisallowedTransitions(ExcursionStatus from, ExcursionStatus to) {
            assertThat(from.canTransitionTo(to)).isFalse();
        }
    }

    @Nested
    @DisplayName("KAN-188: Cập nhật các trạng thái DELAYED, CANCELLED và COMPLETED")
    class StatusUpdateExecutionTests {

        @Test
        @DisplayName("Cập nhật tour sang DELAYED khi gặp trục trặc thời tiết hoặc tàu cập cảng muộn")
        void shouldTransitionToDelayed() {
            excursion.setStatus(ExcursionStatus.OPEN_FOR_BOOKING);
            when(excursionRepository.findById(200L)).thenReturn(Optional.of(excursion));
            when(excursionRepository.save(any(ShoreExcursion.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UpdateExcursionStatusRequest request = UpdateExcursionStatusRequest.builder()
                    .status(ExcursionStatus.DELAYED)
                    .build();

            var response = excursionService.updateStatus(200L, request);

            assertThat(response.getStatus()).isEqualTo(ExcursionStatus.DELAYED);
            assertThat(excursion.getStatus()).isEqualTo(ExcursionStatus.DELAYED);
            verify(excursionRepository).save(excursion);
        }

        @Test
        @DisplayName("Cập nhật tour sang CANCELLED và bắt buộc lưu lại lý do hủy (cancellationReason)")
        void shouldTransitionToCancelledWithReason() {
            when(excursionRepository.findById(200L)).thenReturn(Optional.of(excursion));
            when(excursionRepository.save(any(ShoreExcursion.class))).thenAnswer(invocation -> invocation.getArgument(0));

            String reason = "Cảnh báo giông lốc từ Cảng vụ Hàng hải";
            UpdateExcursionStatusRequest request = UpdateExcursionStatusRequest.builder()
                    .status(ExcursionStatus.CANCELLED)
                    .cancellationReason(reason)
                    .build();

            var response = excursionService.updateStatus(200L, request);

            assertThat(response.getStatus()).isEqualTo(ExcursionStatus.CANCELLED);
            assertThat(response.getCancellationReason()).isEqualTo(reason);
            assertThat(excursion.getCancellationReason()).isEqualTo(reason);
        }

        @Test
        @DisplayName("Cập nhật tour từ IN_PROGRESS sang COMPLETED khi đoàn khách đã về tàu an toàn")
        void shouldTransitionFromInProgressToCompleted() {
            excursion.setStatus(ExcursionStatus.IN_PROGRESS);
            when(excursionRepository.findById(200L)).thenReturn(Optional.of(excursion));
            when(excursionRepository.save(any(ShoreExcursion.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UpdateExcursionStatusRequest request = UpdateExcursionStatusRequest.builder()
                    .status(ExcursionStatus.COMPLETED)
                    .build();

            var response = excursionService.updateStatus(200L, request);

            assertThat(response.getStatus()).isEqualTo(ExcursionStatus.COMPLETED);
            assertThat(excursion.getStatus()).isEqualTo(ExcursionStatus.COMPLETED);
        }

        @Test
        @DisplayName("Từ chối khi tour đã COMPLETED nhưng cố gắng chuyển sang trạng thái khác")
        void shouldRejectTransitionFromCompleted() {
            excursion.setStatus(ExcursionStatus.COMPLETED);
            when(excursionRepository.findById(200L)).thenReturn(Optional.of(excursion));

            UpdateExcursionStatusRequest request = UpdateExcursionStatusRequest.builder()
                    .status(ExcursionStatus.IN_PROGRESS)
                    .build();

            assertThatThrownBy(() -> excursionService.updateStatus(200L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Không thể chuyển trạng thái tour bờ từ 'COMPLETED' sang 'IN_PROGRESS'");
            verify(excursionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Từ chối khi ID tour bờ không tồn tại hoặc đã bị xóa mềm")
        void shouldThrowWhenExcursionNotFound() {
            when(excursionRepository.findById(999L)).thenReturn(Optional.empty());

            UpdateExcursionStatusRequest request = UpdateExcursionStatusRequest.builder()
                    .status(ExcursionStatus.OPEN_FOR_BOOKING)
                    .build();

            assertThatThrownBy(() -> excursionService.updateStatus(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy tour bờ với ID: 999");
        }
    }

    @Nested
    @DisplayName("KAN-189: Theo dõi thời gian tập trung và quay lại tàu (Gathering & Return-to-Ship)")
    class ScheduleTrackingTests {

        @Test
        @DisplayName("Kiểm tra tính nhất quán thứ tự thời gian: gatheringTime < departureTime < returnToShipTime")
        void shouldValidateTimingOrder() {
            LocalDate date = LocalDate.of(2026, 11, 20);
            LocalTime gathering = LocalTime.of(8, 0);
            LocalTime departure = LocalTime.of(8, 30);
            LocalTime returnToShip = LocalTime.of(12, 30);

            // Thứ tự chuẩn không ném ngoại lệ
            excursionService.validateSchedule(date, gathering, departure, returnToShip);

            // Giờ tập trung muộn hơn giờ xuất phát
            assertThatThrownBy(() -> excursionService.validateSchedule(date, LocalTime.of(9, 0), departure, returnToShip))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Giờ khởi hành phải sau giờ tập trung của hành khách");

            // Giờ quay lại tàu trước hoặc bằng giờ xuất phát
            assertThatThrownBy(() -> excursionService.validateSchedule(date, gathering, departure, LocalTime.of(8, 30)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Giờ quay lại tàu phải sau giờ khởi hành tour bờ");
        }

        @Test
        @DisplayName("getExcursionById: Trả về đầy đủ mốc thời gian phục vụ giám sát vận hành tàu")
        void shouldReturnAccurateScheduleInResponse() {
            when(excursionRepository.findById(200L)).thenReturn(Optional.of(excursion));

            var res = excursionService.getExcursionById(200L);

            assertThat(res.getGatheringTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(res.getDepartureTime()).isEqualTo(LocalTime.of(8, 30));
            assertThat(res.getReturnToShipTime()).isEqualTo(LocalTime.of(12, 30));
            assertThat(res.getMeetingPoint()).isEqualTo("Cầu tàu số 1");
            assertThat(res.getPortName()).isEqualTo("Cảng Quốc Tế Tuần Châu");
        }
    }
}
