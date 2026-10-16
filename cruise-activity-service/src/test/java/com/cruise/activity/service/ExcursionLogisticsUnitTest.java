package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.ExcursionGuide;
import com.cruise.activity.entity.ExcursionVehicle;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.enums.GuideRole;
import com.cruise.activity.enums.GuideStatus;
import com.cruise.activity.enums.VehicleStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.ExcursionGuideRepository;
import com.cruise.activity.repository.ExcursionPassengerRepository;
import com.cruise.activity.repository.ExcursionVehicleRepository;
import com.cruise.activity.repository.ShoreExcursionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Excursion Logistics (Vehicles & Tour Guides) Unit Tests (KAN-177)")
class ExcursionLogisticsUnitTest {

    @Mock
    private ShoreExcursionRepository excursionRepository;

    @Mock
    private ExcursionPassengerRepository passengerRepository;

    @Mock
    private ExcursionVehicleRepository vehicleRepository;

    @Mock
    private ExcursionGuideRepository guideRepository;

    @InjectMocks
    private ShoreExcursionService excursionService;

    private ShoreExcursion mockExcursion;
    private ExcursionVehicle mockVehicle;
    private ExcursionGuide mockGuide;

    @BeforeEach
    void setUp() {
        mockExcursion = ShoreExcursion.builder()
                .tourId(1L)
                .portId(10L)
                .dayNumber(1)
                .title("Tour Thăm Vịnh Hạ Long")
                .portName("Cảng Tuần Châu")
                .meetingPoint("Cầu cảng 1")
                .excursionDate(LocalDate.now().plusDays(2))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(12, 0))
                .maxCapacity(50)
                .currentParticipants(0)
                .price(BigDecimal.valueOf(800000))
                .status(ExcursionStatus.SCHEDULED)
                .active(true)
                .build();
        mockExcursion.setId(200L);

        mockVehicle = ExcursionVehicle.builder()
                .excursionId(200L)
                .vehicleCode("VEH-01")
                .licensePlate("14B-012.34")
                .vehicleType("Tàu trung chuyển Tender 60 Chỗ")
                .driverName("Trần Văn Mạnh")
                .driverPhone("+84 903 456 789")
                .capacity(60)
                .assignedPassengers(45)
                .status(VehicleStatus.READY)
                .active(true)
                .build();
        mockVehicle.setId(10L);

        mockGuide = ExcursionGuide.builder()
                .excursionId(200L)
                .guideCode("GUD-01")
                .fullName("Hoàng Hải Đăng")
                .phoneNumber("+84 912 888 999")
                .role(GuideRole.LEAD)
                .languages("Tiếng Việt, English")
                .licenseNumber("HDV-QG-88219")
                .status(GuideStatus.ACTIVE)
                .rating(4.9)
                .active(true)
                .build();
        mockGuide.setId(20L);
    }

    @Nested
    @DisplayName("1. Quản lý Phương tiện vận chuyển (Excursion Vehicles)")
    class VehicleManagementTests {

        @Test
        @DisplayName("Thêm phương tiện mới thành công, tự sinh vehicleCode VEH-01")
        void addVehicle_Success() {
            AddExcursionVehicleRequest request = AddExcursionVehicleRequest.builder()
                    .licensePlate("43B-088.99")
                    .vehicleType("Xe du lịch Universe 45 Chỗ")
                    .driverName("Phạm Hùng Cường")
                    .driverPhone("+84 905 889 900")
                    .capacity(45)
                    .assignedPassengers(0)
                    .status(VehicleStatus.READY)
                    .operatorName("Danang Green Horizon Tours")
                    .notes("Xe đời mới có micro thuyết minh")
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(vehicleRepository.countByExcursionIdAndActiveTrue(200L)).thenReturn(0L);
            when(vehicleRepository.save(any(ExcursionVehicle.class))).thenAnswer(inv -> inv.getArgument(0));

            ShoreExcursionResponse response = excursionService.addVehicleToExcursion(200L, request);

            assertThat(response).isNotNull();
            verify(vehicleRepository).save(argThat(v ->
                    v.getVehicleCode().equals("VEH-01") &&
                    v.getLicensePlate().equals("43B-088.99") &&
                    v.getCapacity() == 45
            ));
        }

        @Test
        @DisplayName("Cập nhật thông tin phương tiện thành công bằng vehicleCode hoặc numeric ID")
        void updateVehicle_Success() {
            UpdateExcursionVehicleRequest request = UpdateExcursionVehicleRequest.builder()
                    .capacity(65)
                    .driverName("Trần Văn Mạnh (Trưởng đội)")
                    .status(VehicleStatus.ON_TRIP)
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(vehicleRepository.findByVehicleCodeAndExcursionIdAndActiveTrue("VEH-01", 200L))
                    .thenReturn(Optional.of(mockVehicle));

            ShoreExcursionResponse response = excursionService.updateVehicleInExcursion(200L, "VEH-01", request);

            assertThat(response).isNotNull();
            assertThat(mockVehicle.getCapacity()).isEqualTo(65);
            assertThat(mockVehicle.getDriverName()).isEqualTo("Trần Văn Mạnh (Trưởng đội)");
            assertThat(mockVehicle.getStatus()).isEqualTo(VehicleStatus.ON_TRIP);
            verify(vehicleRepository).save(mockVehicle);
        }

        @Test
        @DisplayName("Ném lỗi BusinessRuleException khi sửa sức chứa phương tiện <= 0")
        void updateVehicle_InvalidCapacity_ThrowsException() {
            UpdateExcursionVehicleRequest request = UpdateExcursionVehicleRequest.builder()
                    .capacity(0)
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(vehicleRepository.findByVehicleCodeAndExcursionIdAndActiveTrue("VEH-01", 200L))
                    .thenReturn(Optional.of(mockVehicle));

            assertThatThrownBy(() -> excursionService.updateVehicleInExcursion(200L, "VEH-01", request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Sức chứa phương tiện phải lớn hơn 0");
        }

        @Test
        @DisplayName("Xóa phương tiện thành công (soft delete active=false)")
        void removeVehicle_Success() {
            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(vehicleRepository.findByVehicleCodeAndExcursionIdAndActiveTrue("VEH-01", 200L))
                    .thenReturn(Optional.of(mockVehicle));

            ShoreExcursionResponse response = excursionService.removeVehicleFromExcursion(200L, "VEH-01");

            assertThat(response).isNotNull();
            assertThat(mockVehicle.getActive()).isFalse();
            verify(vehicleRepository).save(mockVehicle);
        }

        @Test
        @DisplayName("Ném lỗi ResourceNotFoundException khi không tìm thấy phương tiện")
        void findVehicle_NotFound_ThrowsException() {
            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(vehicleRepository.findByVehicleCodeAndExcursionIdAndActiveTrue("VEH-99", 200L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> excursionService.removeVehicleFromExcursion(200L, "VEH-99"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy phương tiện với ID/Mã 'VEH-99'");
        }
    }

    @Nested
    @DisplayName("2. Quản lý Hướng dẫn viên (Excursion Guides)")
    class GuideManagementTests {

        @Test
        @DisplayName("Thêm hướng dẫn viên mới thành công, tự sinh guideCode GUD-01")
        void addGuide_Success() {
            AddExcursionGuideRequest request = AddExcursionGuideRequest.builder()
                    .fullName("Đặng Mai Phương")
                    .phoneNumber("+84 987 112 233")
                    .role(GuideRole.ASSISTANT)
                    .languages(List.of("Tiếng Việt", "Français"))
                    .licenseNumber("HDV-ND-10492")
                    .status(GuideStatus.ACTIVE)
                    .rating(4.8)
                    .notes("Phụ trách thuyết minh tiếng Pháp")
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(guideRepository.countByExcursionIdAndActiveTrue(200L)).thenReturn(0L);
            when(guideRepository.save(any(ExcursionGuide.class))).thenAnswer(inv -> inv.getArgument(0));

            ShoreExcursionResponse response = excursionService.addGuideToExcursion(200L, request);

            assertThat(response).isNotNull();
            verify(guideRepository).save(argThat(g ->
                    g.getGuideCode().equals("GUD-01") &&
                    g.getFullName().equals("Đặng Mai Phương") &&
                    g.getRole() == GuideRole.ASSISTANT &&
                    g.getLanguages().contains("Français")
            ));
        }

        @Test
        @DisplayName("Cập nhật thông tin hướng dẫn viên thành công")
        void updateGuide_Success() {
            UpdateExcursionGuideRequest request = UpdateExcursionGuideRequest.builder()
                    .role(GuideRole.SPECIALIST)
                    .rating(5.0)
                    .notes("Đạt chứng chỉ hướng dẫn viên xuất sắc 2026")
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(guideRepository.findByGuideCodeAndExcursionIdAndActiveTrue("GUD-01", 200L))
                    .thenReturn(Optional.of(mockGuide));

            ShoreExcursionResponse response = excursionService.updateGuideInExcursion(200L, "GUD-01", request);

            assertThat(response).isNotNull();
            assertThat(mockGuide.getRole()).isEqualTo(GuideRole.SPECIALIST);
            assertThat(mockGuide.getRating()).isEqualTo(5.0);
            verify(guideRepository).save(mockGuide);
        }

        @Test
        @DisplayName("Xóa hướng dẫn viên thành công (soft delete active=false)")
        void removeGuide_Success() {
            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(guideRepository.findByGuideCodeAndExcursionIdAndActiveTrue("GUD-01", 200L))
                    .thenReturn(Optional.of(mockGuide));

            ShoreExcursionResponse response = excursionService.removeGuideFromExcursion(200L, "GUD-01");

            assertThat(response).isNotNull();
            assertThat(mockGuide.getActive()).isFalse();
            verify(guideRepository).save(mockGuide);
        }

        @Test
        @DisplayName("Ném lỗi ResourceNotFoundException khi không tìm thấy hướng dẫn viên")
        void findGuide_NotFound_ThrowsException() {
            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(guideRepository.findByGuideCodeAndExcursionIdAndActiveTrue("GUD-99", 200L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> excursionService.removeGuideFromExcursion(200L, "GUD-99"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy hướng dẫn viên với ID/Mã 'GUD-99'");
        }
    }
}
