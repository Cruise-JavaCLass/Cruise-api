package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.ExcursionGuide;
import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.entity.ExcursionVehicle;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.activity.enums.GuideRole;
import com.cruise.activity.enums.GuideStatus;
import com.cruise.activity.enums.VehicleStatus;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Excursion Group Generation & Manifest Unit Tests (KAN-178)")
class ExcursionGroupGenerationUnitTest {

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
    private List<ExcursionPassenger> mockPassengers;
    private List<ExcursionVehicle> mockVehicles;
    private List<ExcursionGuide> mockGuides;

    @BeforeEach
    void setUp() {
        mockExcursion = ShoreExcursion.builder()
                .tourId(1L)
                .portId(10L)
                .dayNumber(1)
                .title("Tour Thăm Vịnh Hạ Long")
                .excursionDate(LocalDate.now().plusDays(2))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(12, 0))
                .maxCapacity(50)
                .currentParticipants(4)
                .price(BigDecimal.valueOf(800000))
                .active(true)
                .build();
        mockExcursion.setId(300L);

        mockPassengers = new ArrayList<>();

        ExcursionPassenger p1 = ExcursionPassenger.builder()
                .excursionId(300L)
                .passengerName("Nguyễn Văn An")
                .cabinNumber("Suite-101")
                .ticketCode("EXC-TKT-301")
                .groupName("Đoàn 1")
                .busNumber("VEH-01")
                .status(ExcursionPassengerStatus.ON_TOUR)
                .active(true)
                .build();
        p1.setId(1L);

        ExcursionPassenger p2 = ExcursionPassenger.builder()
                .excursionId(300L)
                .passengerName("Trần Thị Bình")
                .cabinNumber("Suite-102")
                .ticketCode("EXC-TKT-302")
                .groupName("Đoàn 1")
                .busNumber("VEH-01")
                .status(ExcursionPassengerStatus.CHECKED_IN)
                .active(true)
                .build();
        p2.setId(2L);

        ExcursionPassenger p3 = ExcursionPassenger.builder()
                .excursionId(300L)
                .passengerName("Lê Hoàng Cúc")
                .cabinNumber("Deluxe-201")
                .ticketCode("EXC-TKT-303")
                .groupName("Đoàn 2")
                .busNumber("VEH-02")
                .status(ExcursionPassengerStatus.REGISTERED)
                .active(true)
                .build();
        p3.setId(3L);

        ExcursionPassenger p4 = ExcursionPassenger.builder()
                .excursionId(300L)
                .passengerName("Phạm Quốc Dũng")
                .cabinNumber("Balcony-301")
                .ticketCode("EXC-TKT-304")
                .groupName(null) // Chưa phân nhóm
                .status(ExcursionPassengerStatus.REGISTERED)
                .active(true)
                .build();
        p4.setId(4L);

        mockPassengers.add(p1);
        mockPassengers.add(p2);
        mockPassengers.add(p3);
        mockPassengers.add(p4);

        mockVehicles = new ArrayList<>();
        ExcursionVehicle v1 = ExcursionVehicle.builder()
                .excursionId(300L)
                .vehicleCode("VEH-01")
                .licensePlate("14B-012.34")
                .vehicleType("Bus 29 Chỗ")
                .driverName("Trần Văn Mạnh")
                .capacity(29)
                .status(VehicleStatus.READY)
                .active(true)
                .build();
        v1.setId(10L);

        ExcursionVehicle v2 = ExcursionVehicle.builder()
                .excursionId(300L)
                .vehicleCode("VEH-02")
                .licensePlate("14B-056.78")
                .vehicleType("Bus 29 Chỗ")
                .driverName("Lê Văn Tuấn")
                .capacity(29)
                .status(VehicleStatus.READY)
                .active(true)
                .build();
        v2.setId(11L);

        mockVehicles.add(v1);
        mockVehicles.add(v2);

        mockGuides = new ArrayList<>();
        ExcursionGuide g1 = ExcursionGuide.builder()
                .excursionId(300L)
                .guideCode("GUD-01")
                .fullName("Hoàng Hải Đăng")
                .role(GuideRole.LEAD)
                .status(GuideStatus.ACTIVE)
                .active(true)
                .build();
        g1.setId(20L);
        mockGuides.add(g1);
    }

    @Nested
    @DisplayName("1. Lấy danh sách hành khách phân nhóm (getPassengerListsByGroup)")
    class GetPassengerListsByGroupTests {

        @Test
        @DisplayName("Lấy danh sách phân nhóm thành công với đầy đủ thông tin nhóm, xe và thống kê")
        void getPassengerListsByGroup_Success() {
            when(excursionRepository.findById(300L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(mockPassengers);
            when(vehicleRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(mockVehicles);
            when(guideRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(mockGuides);

            ExcursionManifestSummaryResponse response = excursionService.getPassengerListsByGroup(300L);

            assertThat(response).isNotNull();
            assertThat(response.getExcursionId()).isEqualTo(300L);
            assertThat(response.getTotalPassengers()).isEqualTo(4);
            assertThat(response.getTotalGroups()).isEqualTo(2); // Đoàn 1, Đoàn 2 (không tính Chưa phân nhóm)

            assertThat(response.getGroups()).hasSize(3);

            // Kiểm tra Đoàn 1
            ExcursionGroupManifestResponse group1 = response.getGroups().stream()
                    .filter(g -> g.getGroupName().equals("Đoàn 1"))
                    .findFirst()
                    .orElse(null);
            assertThat(group1).isNotNull();
            assertThat(group1.getTotalPassengers()).isEqualTo(2);
            assertThat(group1.getOnTourCount()).isEqualTo(1);
            assertThat(group1.getCheckedInCount()).isEqualTo(1);
            assertThat(group1.getPendingCount()).isZero();
            assertThat(group1.getBusNumber()).isEqualTo("VEH-01");
            assertThat(group1.getVehicleInfo()).isNotNull();
            assertThat(group1.getVehicleInfo().getId()).isEqualTo("VEH-01");
            assertThat(group1.getGuideInfo()).isNotNull();
            assertThat(group1.getGuideInfo().getId()).isEqualTo("GUD-01");

            // Kiểm tra Chưa phân nhóm
            ExcursionGroupManifestResponse unassigned = response.getGroups().stream()
                    .filter(g -> g.getGroupName().equals("Chưa phân nhóm"))
                    .findFirst()
                    .orElse(null);
            assertThat(unassigned).isNotNull();
            assertThat(unassigned.getTotalPassengers()).isEqualTo(1);
            assertThat(unassigned.getPassengers().get(0).getPassengerName()).isEqualTo("Phạm Quốc Dũng");
        }

        @Test
        @DisplayName("Xử lý an toàn khi tour chưa có hành khách nào")
        void getPassengerListsByGroup_EmptyPassengers_ReturnsEmptyGroups() {
            when(excursionRepository.findById(300L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(List.of());
            when(vehicleRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(List.of());
            when(guideRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(List.of());

            ExcursionManifestSummaryResponse response = excursionService.getPassengerListsByGroup(300L);

            assertThat(response).isNotNull();
            assertThat(response.getTotalPassengers()).isZero();
            assertThat(response.getTotalGroups()).isZero();
            assertThat(response.getGroups()).isEmpty();
        }
    }

    @Nested
    @DisplayName("2. Tự động sinh và phân bổ nhóm (autoGeneratePassengerGroups)")
    class AutoGenerateGroupsTests {

        @Test
        @DisplayName("Tự động phân nhóm theo targetGroupCount và tự gán xe có sẵn")
        void autoGenerate_WithTargetGroupCount_Success() {
            when(excursionRepository.findById(300L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(mockPassengers);
            when(vehicleRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(mockVehicles);
            when(guideRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(mockGuides);

            AutoGenerateGroupsRequest request = AutoGenerateGroupsRequest.builder()
                    .targetGroupCount(2)
                    .groupPrefix("Nhóm ")
                    .autoAssignVehicles(true)
                    .build();

            ExcursionManifestSummaryResponse response = excursionService.autoGeneratePassengerGroups(300L, request);

            assertThat(response).isNotNull();
            verify(passengerRepository).saveAll(anyList());
            assertThat(mockPassengers.get(0).getGroupName()).startsWith("Nhóm ");
            assertThat(mockPassengers.get(0).getBusNumber()).isNotNull();
        }

        @Test
        @DisplayName("Tự động phân nhóm dựa trên số xe khả dụng khi không truyền targetGroupCount")
        void autoGenerate_DefaultToVehicleCount_Success() {
            when(excursionRepository.findById(300L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(mockPassengers);
            when(vehicleRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(mockVehicles);
            when(guideRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(mockGuides);

            AutoGenerateGroupsRequest request = AutoGenerateGroupsRequest.builder()
                    .autoAssignVehicles(false)
                    .build();

            ExcursionManifestSummaryResponse response = excursionService.autoGeneratePassengerGroups(300L, request);

            assertThat(response).isNotNull();
            verify(passengerRepository).saveAll(anyList());
            assertThat(mockPassengers.get(0).getGroupName()).startsWith("Đoàn ");
        }

        @Test
        @DisplayName("Tự động phân nhóm dựa trên maxPassengersPerGroup khi không có xe")
        void autoGenerate_WithMaxPassengersPerGroup_Success() {
            when(excursionRepository.findById(300L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(mockPassengers);
            when(vehicleRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(List.of());
            when(guideRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(List.of());

            AutoGenerateGroupsRequest request = AutoGenerateGroupsRequest.builder()
                    .maxPassengersPerGroup(2)
                    .build();

            ExcursionManifestSummaryResponse response = excursionService.autoGeneratePassengerGroups(300L, request);

            assertThat(response).isNotNull();
            verify(passengerRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("Xử lý an toàn khi không có hành khách hợp lệ để phân nhóm")
        void autoGenerate_NoEligiblePassengers_ReturnsCurrent() {
            when(excursionRepository.findById(300L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(List.of());
            when(vehicleRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(List.of());
            when(guideRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(List.of());

            ExcursionManifestSummaryResponse response = excursionService.autoGeneratePassengerGroups(300L, null);

            assertThat(response).isNotNull();
            verify(passengerRepository, never()).saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("3. Xuất danh sách hành khách theo nhóm dạng CSV (exportPassengerGroupsCsv)")
    class ExportPassengerGroupsCsvTests {

        @Test
        @DisplayName("Xuất toàn bộ danh sách hành khách theo nhóm dạng CSV thành công có UTF-8 BOM")
        void exportPassengerGroupsCsv_AllGroups_Success() {
            when(excursionRepository.findById(300L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(mockPassengers);
            when(vehicleRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(mockVehicles);
            when(guideRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(mockGuides);

            byte[] csvBytes = excursionService.exportPassengerGroupsCsv(300L, null);

            assertThat(csvBytes).isNotEmpty();
            String csvContent = new String(csvBytes, StandardCharsets.UTF_8);

            // Kiểm tra UTF-8 BOM
            assertThat(csvContent.charAt(0)).isEqualTo('\ufeff');
            // Kiểm tra header
            assertThat(csvContent).contains("STT,Đoàn / Nhóm,Xe vận chuyển,Mã vé,Họ và tên,Số cabin,Trạng thái");
            // Kiểm tra các dòng dữ liệu
            assertThat(csvContent).contains("Nguyễn Văn An");
            assertThat(csvContent).contains("Trần Thị Bình");
            assertThat(csvContent).contains("Lê Hoàng Cúc");
            assertThat(csvContent).contains("Phạm Quốc Dũng");
        }

        @Test
        @DisplayName("Xuất danh sách hành khách theo một nhóm cụ thể có áp dụng bộ lọc")
        void exportPassengerGroupsCsv_FilteredByGroup_Success() {
            when(excursionRepository.findById(300L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(mockPassengers);
            when(vehicleRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(mockVehicles);
            when(guideRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(mockGuides);

            byte[] csvBytes = excursionService.exportPassengerGroupsCsv(300L, "Đoàn 1");

            assertThat(csvBytes).isNotEmpty();
            String csvContent = new String(csvBytes, StandardCharsets.UTF_8);

            assertThat(csvContent).contains("Nguyễn Văn An");
            assertThat(csvContent).contains("Trần Thị Bình");
            assertThat(csvContent).doesNotContain("Lê Hoàng Cúc");
            assertThat(csvContent).doesNotContain("Phạm Quốc Dũng");
        }

        @Test
        @DisplayName("Kiểm tra định dạng thoát ký tự đặc biệt trong CSV (dấu phẩy, ngoặc kép, xuống dòng)")
        void exportPassengerGroupsCsv_SpecialCharacters_EscapedProperly() {
            ExcursionPassenger specialPassenger = ExcursionPassenger.builder()
                    .excursionId(300L)
                    .passengerName("Trần \"Vip\", Hải")
                    .cabinNumber("Cabin\n101")
                    .ticketCode("TKT-SPECIAL")
                    .groupName("Đoàn Đặc Biệt")
                    .busNumber("Bus, Special")
                    .specialRequirements("Dị ứng hải sản\nCần xe lăn")
                    .status(ExcursionPassengerStatus.REGISTERED)
                    .active(true)
                    .build();

            when(excursionRepository.findById(300L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(List.of(specialPassenger));
            when(vehicleRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(List.of());
            when(guideRepository.findByExcursionIdAndActiveTrue(300L)).thenReturn(List.of());

            byte[] csvBytes = excursionService.exportPassengerGroupsCsv(300L, null);
            String csv = new String(csvBytes, StandardCharsets.UTF_8);

            assertThat(csv).contains("\"Trần \"\"Vip\"\", Hải\"");
            assertThat(csv).contains("\"Bus, Special\"");
        }
    }

    @Nested
    @DisplayName("4. Kiểm thử DTOs (Getters, Setters, Builders)")
    class DtoTests {

        @Test
        @DisplayName("ExcursionGroupManifestResponse & ExcursionManifestSummaryResponse DTO coverage")
        void dtoCoverage() {
            ExcursionGroupManifestResponse groupResp = new ExcursionGroupManifestResponse();
            groupResp.setGroupName("Nhóm A");
            groupResp.setBusNumber("XE-99");
            groupResp.setTotalPassengers(10);
            groupResp.setCheckedInCount(5);
            groupResp.setOnTourCount(3);
            groupResp.setReturnedSafeCount(0);
            groupResp.setPendingCount(2);
            groupResp.setPassengers(new ArrayList<>());

            assertThat(groupResp.getGroupName()).isEqualTo("Nhóm A");
            assertThat(groupResp.getBusNumber()).isEqualTo("XE-99");

            ExcursionManifestSummaryResponse summaryResp = new ExcursionManifestSummaryResponse();
            summaryResp.setExcursionId(1L);
            summaryResp.setExcursionTitle("Title");
            summaryResp.setExcursionDate(LocalDate.now());
            summaryResp.setGatheringTime(LocalTime.of(8, 0));
            summaryResp.setDepartureTime(LocalTime.of(8, 30));
            summaryResp.setReturnToShipTime(LocalTime.of(12, 0));
            summaryResp.setTotalPassengers(10);
            summaryResp.setTotalGroups(1);
            summaryResp.setGroups(List.of(groupResp));

            assertThat(summaryResp.getExcursionId()).isEqualTo(1L);
            assertThat(summaryResp.getGroups()).hasSize(1);

            AutoGenerateGroupsRequest req = new AutoGenerateGroupsRequest();
            req.setTargetGroupCount(3);
            req.setMaxPassengersPerGroup(15);
            req.setGroupPrefix("Đoàn ");
            req.setAutoAssignVehicles(true);

            assertThat(req.getTargetGroupCount()).isEqualTo(3);
            assertThat(req.getMaxPassengersPerGroup()).isEqualTo(15);
            assertThat(req.getGroupPrefix()).isEqualTo("Đoàn ");
            assertThat(req.getAutoAssignVehicles()).isTrue();
        }
    }
}
