package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.ShoreExcursionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShoreExcursionService Unit Tests (KAN-168)")
class ShoreExcursionServiceTest {

    @Mock
    private ShoreExcursionRepository excursionRepository;

    @InjectMocks
    private ShoreExcursionService excursionService;

    private ShoreExcursion sampleExcursion;

    @BeforeEach
    void setUp() {
        sampleExcursion = ShoreExcursion.builder()
                .tourId(1L)
                .portId(2L)
                .dayNumber(2)
                .title("Khám phá Hang Sửng Sốt & Chèo Kayak")
                .description("Tour khám phá hang động đẹp nhất Vịnh Hạ Long")
                .portName("Cảng Quốc Tế Hạ Long")
                .meetingPoint("Cầu cảng số 2")
                .localProvider("Halong Bay Eco Tours")
                .providerContact("+84 912 345 678")
                .excursionDate(LocalDate.of(2026, 11, 2))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(12, 0))
                .maxCapacity(50)
                .currentParticipants(10)
                .price(BigDecimal.valueOf(1200000))
                .childPrice(BigDecimal.valueOf(600000))
                .currency("VND")
                .isIncluded(false)
                .status(ExcursionStatus.SCHEDULED)
                .active(true)
                .build();
        sampleExcursion.setId(100L);
    }

    @Test
    @DisplayName("createExcursion: should succeed with valid schedule, price and capacity")
    void createExcursion_Success() {
        CreateShoreExcursionRequest request = CreateShoreExcursionRequest.builder()
                .tourId(1L)
                .portId(2L)
                .dayNumber(2)
                .title("Tour Lặn Biển San Hô")
                .portName("Cảng Quốc Tế Hạ Long")
                .meetingPoint("Cầu tàu A")
                .excursionDate(LocalDate.of(2026, 11, 2))
                .gatheringTime(LocalTime.of(9, 0))
                .departureTime(LocalTime.of(9, 30))
                .returnToShipTime(LocalTime.of(13, 0))
                .maxCapacity(40)
                .price(BigDecimal.valueOf(800000))
                .childPrice(BigDecimal.valueOf(400000))
                .build();

        when(excursionRepository.existsDuplicateExcursion(any(), any(), any(), any(), isNull()))
                .thenReturn(false);
        when(excursionRepository.save(any(ShoreExcursion.class))).thenAnswer(invocation -> {
            ShoreExcursion saved = invocation.getArgument(0);
            saved.setId(101L);
            return saved;
        });

        ShoreExcursionResponse response = excursionService.createExcursion(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getTitle()).isEqualTo("Tour Lặn Biển San Hô");
        assertThat(response.getRemainingCapacity()).isEqualTo(40);
        assertThat(response.getStatus()).isEqualTo(ExcursionStatus.SCHEDULED);
        verify(excursionRepository).save(any(ShoreExcursion.class));
    }

    @Test
    @DisplayName("configurePriceCapacityAndSchedule: should successfully update price, capacity and times (KAN-168)")
    void configurePriceCapacityAndSchedule_Success() {
        ConfigureExcursionPriceCapacityScheduleRequest req = ConfigureExcursionPriceCapacityScheduleRequest.builder()
                .excursionDate(LocalDate.of(2026, 11, 2))
                .gatheringTime(LocalTime.of(7, 45))
                .departureTime(LocalTime.of(8, 15))
                .returnToShipTime(LocalTime.of(12, 30))
                .maxCapacity(60)
                .price(BigDecimal.valueOf(1350000))
                .childPrice(BigDecimal.valueOf(700000))
                .isIncluded(false)
                .build();

        when(excursionRepository.findById(100L)).thenReturn(Optional.of(sampleExcursion));
        when(excursionRepository.save(any(ShoreExcursion.class))).thenAnswer(i -> i.getArgument(0));

        ShoreExcursionResponse res = excursionService.configurePriceCapacityAndSchedule(100L, req);

        assertThat(res).isNotNull();
        assertThat(res.getMaxCapacity()).isEqualTo(60);
        assertThat(res.getRemainingCapacity()).isEqualTo(50); // 60 - 10 currentParticipants
        assertThat(res.getPrice()).isEqualTo(BigDecimal.valueOf(1350000));
        assertThat(res.getChildPrice()).isEqualTo(BigDecimal.valueOf(700000));
        assertThat(res.getGatheringTime()).isEqualTo(LocalTime.of(7, 45));
        assertThat(res.getDepartureTime()).isEqualTo(LocalTime.of(8, 15));
        assertThat(res.getReturnToShipTime()).isEqualTo(LocalTime.of(12, 30));
    }

    @Test
    @DisplayName("configurePriceCapacityAndSchedule: throws BusinessRuleException when max capacity is lower than current participants")
    void configurePriceCapacityAndSchedule_CapacityLowerThanParticipants() {
        ConfigureExcursionPriceCapacityScheduleRequest req = ConfigureExcursionPriceCapacityScheduleRequest.builder()
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(12, 0))
                .maxCapacity(5) // currentParticipants is 10
                .price(BigDecimal.valueOf(1000000))
                .build();

        when(excursionRepository.findById(100L)).thenReturn(Optional.of(sampleExcursion));

        assertThatThrownBy(() -> excursionService.configurePriceCapacityAndSchedule(100L, req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Sức chứa tối đa (5) không được nhỏ hơn số người đã đăng ký hiện tại (10)");
    }

    @Test
    @DisplayName("configurePriceCapacityAndSchedule: throws ResourceNotFoundException when excursion does not exist")
    void configurePriceCapacityAndSchedule_NotFound() {
        ConfigureExcursionPriceCapacityScheduleRequest req = ConfigureExcursionPriceCapacityScheduleRequest.builder()
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(12, 0))
                .maxCapacity(50)
                .price(BigDecimal.valueOf(1000000))
                .build();

        when(excursionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> excursionService.configurePriceCapacityAndSchedule(999L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy tour bờ với ID: 999");
    }

    @Test
    @DisplayName("validateSchedule: throws BusinessRuleException when departure time is not after gathering time")
    void validateSchedule_DepartureBeforeGathering() {
        assertThatThrownBy(() -> excursionService.validateSchedule(
                LocalDate.of(2026, 11, 2),
                LocalTime.of(9, 0),
                LocalTime.of(8, 30), // before gathering
                LocalTime.of(12, 0)
        )).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Giờ khởi hành phải sau giờ tập trung");
    }

    @Test
    @DisplayName("validateSchedule: throws BusinessRuleException when return time is not after departure time")
    void validateSchedule_ReturnBeforeDeparture() {
        assertThatThrownBy(() -> excursionService.validateSchedule(
                LocalDate.of(2026, 11, 2),
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                LocalTime.of(8, 45) // before departure
        )).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Giờ quay lại tàu phải sau giờ khởi hành");
    }

    @Test
    @DisplayName("validatePriceAndCapacity: throws BusinessRuleException when child price is higher than adult price")
    void validatePriceAndCapacity_ChildPriceHigher() {
        assertThatThrownBy(() -> excursionService.validatePriceAndCapacity(
                BigDecimal.valueOf(500000),
                BigDecimal.valueOf(600000), // higher than adult
                50
        )).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Giá vé trẻ em không được cao hơn giá vé người lớn");
    }

    @Test
    @DisplayName("validatePriceAndCapacity: throws BusinessRuleException when capacity <= 0")
    void validatePriceAndCapacity_InvalidCapacity() {
        assertThatThrownBy(() -> excursionService.validatePriceAndCapacity(
                BigDecimal.valueOf(500000),
                BigDecimal.valueOf(300000),
                0
        )).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Sức chứa tối đa của tour bờ phải lớn hơn 0");
    }

    @Test
    @DisplayName("validatePriceAndCapacity: throws BusinessRuleException when price is negative")
    void validatePriceAndCapacity_NegativePrice() {
        assertThatThrownBy(() -> excursionService.validatePriceAndCapacity(
                BigDecimal.valueOf(-100),
                null,
                50
        )).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Giá vé người lớn không được để trống hoặc âm");
    }

    @Test
    @DisplayName("updateExcursion: successfully updates all fields")
    void updateExcursion_Success() {
        UpdateShoreExcursionRequest req = UpdateShoreExcursionRequest.builder()
                .portId(2L)
                .dayNumber(2)
                .title("Khám phá Hang Sửng Sốt VIP")
                .description("Bổ sung hướng dẫn viên chuyên nghiệp")
                .portName("Cảng Quốc Tế Hạ Long")
                .meetingPoint("Cầu tàu 3")
                .excursionDate(LocalDate.of(2026, 11, 2))
                .gatheringTime(LocalTime.of(8, 15))
                .departureTime(LocalTime.of(8, 45))
                .returnToShipTime(LocalTime.of(12, 15))
                .maxCapacity(70)
                .price(BigDecimal.valueOf(1500000))
                .childPrice(BigDecimal.valueOf(750000))
                .build();

        when(excursionRepository.findById(100L)).thenReturn(Optional.of(sampleExcursion));
        when(excursionRepository.existsDuplicateExcursion(any(), any(), any(), any(), eq(100L)))
                .thenReturn(false);
        when(excursionRepository.save(any(ShoreExcursion.class))).thenAnswer(i -> i.getArgument(0));

        ShoreExcursionResponse res = excursionService.updateExcursion(100L, req);

        assertThat(res).isNotNull();
        assertThat(res.getTitle()).isEqualTo("Khám phá Hang Sửng Sốt VIP");
        assertThat(res.getMaxCapacity()).isEqualTo(70);
    }

    @Test
    @DisplayName("updateStatus: transitions to OPEN_FOR_BOOKING successfully")
    void updateStatus_Success() {
        when(excursionRepository.findById(100L)).thenReturn(Optional.of(sampleExcursion));
        when(excursionRepository.save(any(ShoreExcursion.class))).thenAnswer(i -> i.getArgument(0));

        UpdateExcursionStatusRequest req = UpdateExcursionStatusRequest.builder()
                .status(ExcursionStatus.OPEN_FOR_BOOKING)
                .build();

        ShoreExcursionResponse res = excursionService.updateStatus(100L, req);
        assertThat(res.getStatus()).isEqualTo(ExcursionStatus.OPEN_FOR_BOOKING);
    }

    @Test
    @DisplayName("updateStatus: transitions to CANCELLED with reason")
    void updateStatus_CancelledWithReason() {
        when(excursionRepository.findById(100L)).thenReturn(Optional.of(sampleExcursion));
        when(excursionRepository.save(any(ShoreExcursion.class))).thenAnswer(i -> i.getArgument(0));

        UpdateExcursionStatusRequest req = UpdateExcursionStatusRequest.builder()
                .status(ExcursionStatus.CANCELLED)
                .cancellationReason("Bão biển cấp 8")
                .build();

        ShoreExcursionResponse res = excursionService.updateStatus(100L, req);
        assertThat(res.getStatus()).isEqualTo(ExcursionStatus.CANCELLED);
        assertThat(res.getCancellationReason()).isEqualTo("Bão biển cấp 8");
    }

    @Test
    @DisplayName("updateStatus: throws BusinessRuleException when invalid transition")
    void updateStatus_InvalidTransition() {
        when(excursionRepository.findById(100L)).thenReturn(Optional.of(sampleExcursion));

        UpdateExcursionStatusRequest req = UpdateExcursionStatusRequest.builder()
                .status(ExcursionStatus.COMPLETED) // SCHEDULED -> COMPLETED not allowed
                .build();

        assertThatThrownBy(() -> excursionService.updateStatus(100L, req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Không thể chuyển trạng thái tour bờ");
    }

    @Test
    @DisplayName("getAllowedStatusTransitions: returns valid transition set")
    void getAllowedStatusTransitions_Success() {
        when(excursionRepository.findById(100L)).thenReturn(Optional.of(sampleExcursion));

        Set<ExcursionStatus> transitions = excursionService.getAllowedStatusTransitions(100L);
        assertThat(transitions).containsExactlyInAnyOrder(
                ExcursionStatus.OPEN_FOR_BOOKING, ExcursionStatus.DELAYED, ExcursionStatus.CANCELLED
        );
    }

    @Test
    @DisplayName("getAllowedStatusTransitions: throws ResourceNotFoundException when excursion not found")
    void getAllowedStatusTransitions_NotFound() {
        when(excursionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> excursionService.getAllowedStatusTransitions(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy tour bờ với ID: 999");
    }

    @Test
    @DisplayName("getExcursionsByTourAndStatus: returns list of excursions with specific status")
    void getExcursionsByTourAndStatus_Success() {
        when(excursionRepository.findByTourIdAndStatusAndActiveTrueOrderByGatheringTimeAsc(1L, ExcursionStatus.OPEN_FOR_BOOKING))
                .thenReturn(List.of(sampleExcursion));

        List<ShoreExcursionResponse> list = excursionService.getExcursionsByTourAndStatus(1L, ExcursionStatus.OPEN_FOR_BOOKING);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTitle()).isEqualTo("Khám phá Hang Sửng Sốt & Chèo Kayak");
    }

    @Test
    @DisplayName("getExcursionsByTour: returns list of excursions")
    void getExcursionsByTour_Success() {
        when(excursionRepository.findByTourIdAndActiveTrueOrderByExcursionDateAscGatheringTimeAsc(1L))
                .thenReturn(List.of(sampleExcursion));

        List<ShoreExcursionResponse> list = excursionService.getExcursionsByTour(1L);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTitle()).isEqualTo("Khám phá Hang Sửng Sốt & Chèo Kayak");
    }

    @Test
    @DisplayName("getExcursionsPaged: returns paged response")
    void getExcursionsPaged_Success() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(excursionRepository.findPagedExcursions(1L, 2L, 2, "Hang", pageable))
                .thenReturn(new PageImpl<>(List.of(sampleExcursion), pageable, 1));

        Page<ShoreExcursionResponse> page = excursionService.getExcursionsPaged(1L, 2L, 2, "Hang", pageable);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("deleteExcursion: soft deletes excursion and sets CANCELLED")
    void deleteExcursion_Success() {
        when(excursionRepository.findById(100L)).thenReturn(Optional.of(sampleExcursion));

        excursionService.deleteExcursion(100L);

        assertThat(sampleExcursion.getActive()).isFalse();
        assertThat(sampleExcursion.getStatus()).isEqualTo(ExcursionStatus.CANCELLED);
        verify(excursionRepository).save(sampleExcursion);
    }
}
