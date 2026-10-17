package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.ExcursionGuide;
import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.entity.ExcursionVehicle;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionPassengerStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Bộ kiểm thử đơn vị chuyên sâu cho toàn bộ các quy tắc nghiệp vụ phân nhóm và điều phối tour bờ (KAN-179):
 * 1. Quy tắc gán nhóm đơn lẻ (Single Group Assignment Rules)
 * 2. Quy tắc gán nhóm hàng loạt (Bulk Group Assignment Rules)
 * 3. Quy tắc tự động phân nhóm và phân bổ xe (Auto-Generation Rules)
 * 4. Quy tắc quản lý xe và hướng dẫn viên đồng bộ theo đoàn (Vehicle & Guide Logistics Rules)
 * 5. Quy tắc toàn vẹn dữ liệu và kiểm soát lỗi biên (Edge Cases & Exception Rules)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests for Excursion Group Assignment Rules (KAN-179)")
class ExcursionGroupAssignmentRulesUnitTest {

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
    private List<ExcursionPassenger> samplePassengers;
    private List<ExcursionVehicle> sampleVehicles;
    private List<ExcursionGuide> sampleGuides;

    @BeforeEach
    void setUp() {
        mockExcursion = ShoreExcursion.builder()
                .tourId(1L)
                .portId(10L)
                .dayNumber(2)
                .title("Tour Thám Hiểm Hang Sửng Sốt & Đảo Titop")
                .portName("Cảng Quốc Tế Tuần Châu")
                .meetingPoint("Cầu Tàu Số 2")
                .excursionDate(LocalDate.now().plusDays(3))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(12, 30))
                .maxCapacity(60)
                .currentParticipants(6)
                .price(BigDecimal.valueOf(750000))
                .childPrice(BigDecimal.valueOf(400000))
                .currency("VND")
                .status(ExcursionStatus.OPEN_FOR_BOOKING)
                .active(true)
                .build();
        mockExcursion.setId(200L);

        samplePassengers = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            ExcursionPassenger p = ExcursionPassenger.builder()
                    .excursionId(200L)
                    .passengerName("Hành khách " + i)
                    .cabinNumber("Cabin-" + (100 + i))
                    .ticketCode(String.format("EXC-TKT-200-%03d", i))
                    .groupName(i <= 3 ? "Đoàn A" : null)
                    .busNumber(i <= 3 ? "VEH-01" : null)
                    .status(i == 1 ? ExcursionPassengerStatus.CHECKED_IN : ExcursionPassengerStatus.REGISTERED)
                    .emergencyPhone("+84 901 000 00" + i)
                    .active(true)
                    .build();
            p.setId((long) i);
            samplePassengers.add(p);
        }

        sampleVehicles = new ArrayList<>();
        ExcursionVehicle v1 = ExcursionVehicle.builder()
                .excursionId(200L)
                .vehicleCode("VEH-01")
                .licensePlate("14B-888.88")
                .vehicleType("Xe 29 Chỗ Huyndai County")
                .driverName("Nguyễn Văn Tài")
                .driverPhone("+84 912 345 678")
                .capacity(29)
                .assignedPassengers(3)
                .status(VehicleStatus.READY)
                .active(true)
                .build();
        v1.setId(101L);

        ExcursionVehicle v2 = ExcursionVehicle.builder()
                .excursionId(200L)
                .vehicleCode("VEH-02")
                .licensePlate("14B-999.99")
                .vehicleType("Xe 45 Chỗ Universe")
                .driverName("Trần Văn Đức")
                .driverPhone("+84 987 654 321")
                .capacity(45)
                .assignedPassengers(0)
                .status(VehicleStatus.READY)
                .active(true)
                .build();
        v2.setId(102L);
        sampleVehicles.add(v1);
        sampleVehicles.add(v2);

        sampleGuides = new ArrayList<>();
        ExcursionGuide g1 = ExcursionGuide.builder()
                .excursionId(200L)
                .guideCode("GUD-01")
                .fullName("Lê Hoàng Nam")
                .phoneNumber("+84 933 222 111")
                .role(GuideRole.LEAD)
                .languages("Tiếng Việt, Tiếng Anh")
                .licenseNumber("HDV-QN-2026-01")
                .status(GuideStatus.ACTIVE)
                .rating(4.9)
                .active(true)
                .build();
        g1.setId(201L);
        sampleGuides.add(g1);
    }

    // =========================================================================
    // 1. QUY TẮC GÁN NHÓM ĐƠN LẺ (Single Passenger Assignment Rules)
    // =========================================================================
    @Nested
    @DisplayName("1. Quy tắc gán nhóm và xe cho từng hành khách (Single Assignment Rules)")
    class SinglePassengerGroupAssignmentRulesTests {

        @Test
        @DisplayName("Gán nhóm và xe mới thành công cho hành khách hợp lệ")
        void assignGroup_ValidPassenger_Success() {
            ExcursionPassenger targetPassenger = samplePassengers.get(3); // Chưa phân nhóm
            AssignPassengerGroupRequest request = AssignPassengerGroupRequest.builder()
                    .groupName("Đoàn B - Leo Núi")
                    .busNumber("VEH-02")
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(4L, 200L))
                    .thenReturn(Optional.of(targetPassenger));
            when(passengerRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(samplePassengers);

            ShoreExcursionResponse response = excursionService.assignPassengerToGroup(200L, 4L, request);

            assertThat(response).isNotNull();
            assertThat(targetPassenger.getGroupName()).isEqualTo("Đoàn B - Leo Núi");
            assertThat(targetPassenger.getBusNumber()).isEqualTo("VEH-02");
            verify(passengerRepository).save(targetPassenger);
        }

        @Test
        @DisplayName("Chuyển đổi nhóm thành công cho hành khách đã ở trong đoàn khác (Re-assignment)")
        void reassignGroup_PassengerAlreadyInGroup_UpdatesSuccessfully() {
            ExcursionPassenger passengerInGroupA = samplePassengers.get(0); // Đang ở Đoàn A, VEH-01
            AssignPassengerGroupRequest request = AssignPassengerGroupRequest.builder()
                    .groupName("Đoàn C - Chèo Kayak")
                    .busNumber("VEH-02")
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(1L, 200L))
                    .thenReturn(Optional.of(passengerInGroupA));
            when(passengerRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(samplePassengers);

            excursionService.assignPassengerToGroup(200L, 1L, request);

            assertThat(passengerInGroupA.getGroupName()).isEqualTo("Đoàn C - Chèo Kayak");
            assertThat(passengerInGroupA.getBusNumber()).isEqualTo("VEH-02");
            verify(passengerRepository).save(passengerInGroupA);
        }

        @Test
        @DisplayName("Cắt khoảng trắng thừa khi gán tên nhóm và xe")
        void assignGroup_TrimsWhitespaceProperly() {
            ExcursionPassenger passengerInGroupA = samplePassengers.get(1);
            AssignPassengerGroupRequest request = AssignPassengerGroupRequest.builder()
                    .groupName("  Đoàn C - Biển Chiều  ")
                    .busNumber("  VEH-02  ")
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(2L, 200L))
                    .thenReturn(Optional.of(passengerInGroupA));
            when(passengerRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(samplePassengers);

            excursionService.assignPassengerToGroup(200L, 2L, request);

            assertThat(passengerInGroupA.getGroupName()).isEqualTo("Đoàn C - Biển Chiều");
            assertThat(passengerInGroupA.getBusNumber()).isEqualTo("VEH-02");
            verify(passengerRepository).save(passengerInGroupA);
        }

        @Test
        @DisplayName("Từ chối gán nhóm khi tour bờ không tồn tại hoặc đã bị xóa mềm (active=false)")
        void assignGroup_ExcursionNotFound_ThrowsResourceNotFoundException() {
            when(excursionRepository.findById(999L)).thenReturn(Optional.empty());

            AssignPassengerGroupRequest request = AssignPassengerGroupRequest.builder()
                    .groupName("Đoàn VIP")
                    .build();

            assertThatThrownBy(() -> excursionService.assignPassengerToGroup(999L, 1L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy tour bờ với ID: 999");

            verify(passengerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Từ chối gán nhóm khi hành khách không thuộc tour bờ hoặc đã bị hủy đăng ký")
        void assignGroup_PassengerNotFoundInTour_ThrowsResourceNotFoundException() {
            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(999L, 200L))
                    .thenReturn(Optional.empty());

            AssignPassengerGroupRequest request = AssignPassengerGroupRequest.builder()
                    .groupName("Đoàn 1")
                    .build();

            assertThatThrownBy(() -> excursionService.assignPassengerToGroup(200L, 999L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy hành khách với ID: 999 trong tour bờ ID: 200");

            verify(passengerRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 2. QUY TẮC GÁN NHÓM HÀNG LOẠT (Bulk Passenger Assignment Rules)
    // =========================================================================
    @Nested
    @DisplayName("2. Quy tắc gán nhóm và xe hàng loạt (Bulk Assignment Rules)")
    class BulkPassengerGroupAssignmentRulesTests {

        @Test
        @DisplayName("Gán nhóm và xe hàng loạt cho danh sách ID hành khách hợp lệ")
        void bulkAssign_AllValidIds_UpdatesAllPassengers() {
            List<Long> targetIds = List.of(4L, 5L, 6L);
            List<ExcursionPassenger> targetList = List.of(samplePassengers.get(3), samplePassengers.get(4), samplePassengers.get(5));

            BulkAssignPassengerGroupRequest request = BulkAssignPassengerGroupRequest.builder()
                    .passengerIds(targetIds)
                    .groupName("Đoàn B - Du Thuyền VIP")
                    .busNumber("VEH-02")
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndIdInAndActiveTrue(200L, targetIds))
                    .thenReturn(targetList);
            when(passengerRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(samplePassengers);

            ShoreExcursionResponse response = excursionService.bulkAssignPassengersToGroup(200L, request);

            assertThat(response).isNotNull();
            for (ExcursionPassenger p : targetList) {
                assertThat(p.getGroupName()).isEqualTo("Đoàn B - Du Thuyền VIP");
                assertThat(p.getBusNumber()).isEqualTo("VEH-02");
            }

            verify(passengerRepository).saveAll(targetList);
        }

        @Test
        @DisplayName("Chỉ cập nhật những hành khách hợp lệ khi danh sách ID có chứa ID không tồn tại (Partial Match)")
        void bulkAssign_PartialMatchIds_UpdatesOnlyExistingEligiblePassengers() {
            List<Long> mixedIds = List.of(4L, 999L);
            List<ExcursionPassenger> existingMatch = List.of(samplePassengers.get(3));

            BulkAssignPassengerGroupRequest request = BulkAssignPassengerGroupRequest.builder()
                    .passengerIds(mixedIds)
                    .groupName("Đoàn Phụ")
                    .busNumber("VEH-01")
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndIdInAndActiveTrue(200L, mixedIds))
                    .thenReturn(existingMatch);
            when(passengerRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(samplePassengers);

            excursionService.bulkAssignPassengersToGroup(200L, request);

            assertThat(samplePassengers.get(3).getGroupName()).isEqualTo("Đoàn Phụ");
            verify(passengerRepository).saveAll(existingMatch);
        }

        @Test
        @DisplayName("Từ chối gán hàng loạt khi không tìm thấy bất kỳ ID hành khách hợp lệ nào")
        void bulkAssign_NoMatchingPassengers_ThrowsBusinessRuleException() {
            List<Long> invalidIds = List.of(901L, 902L);

            BulkAssignPassengerGroupRequest request = BulkAssignPassengerGroupRequest.builder()
                    .passengerIds(invalidIds)
                    .groupName("Đoàn Trống")
                    .busNumber("VEH-01")
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndIdInAndActiveTrue(200L, invalidIds))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> excursionService.bulkAssignPassengersToGroup(200L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Không tìm thấy hành khách hợp lệ nào trong tour bờ để gán nhóm");

            verify(passengerRepository, never()).saveAll(any());
        }
    }

    // =========================================================================
    // 3. QUY TẮC TỰ ĐỘNG PHÂN BỔ NHÓM VÀ XE (Auto-Generation Rules)
    // =========================================================================
    @Nested
    @DisplayName("3. Quy tắc thuật toán tự động phân nhóm và phân bổ xe (Auto-Generation Rules)")
    class AutoGenerationAlgorithmRulesTests {

        @Test
        @DisplayName("Quy tắc 1: Phân bổ đều vòng tròn (Round-Robin) khi chỉ định targetGroupCount")
        void autoGenerate_TargetGroupCount_DistributesEvenlyAcrossGroups() {
            AutoGenerateGroupsRequest request = AutoGenerateGroupsRequest.builder()
                    .targetGroupCount(3)
                    .groupPrefix("Đoàn Khám Phá")
                    .autoAssignVehicles(false)
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(samplePassengers);
            when(vehicleRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(sampleVehicles);
            when(guideRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(sampleGuides);

            ExcursionManifestSummaryResponse response = excursionService.autoGeneratePassengerGroups(200L, request);

            assertThat(response).isNotNull();
            // Với 6 khách chia 3 nhóm: Nhóm 1 (p1, p4), Nhóm 2 (p2, p5), Nhóm 3 (p3, p6)
            assertThat(samplePassengers.get(0).getGroupName()).isEqualTo("Đoàn Khám Phá 1");
            assertThat(samplePassengers.get(1).getGroupName()).isEqualTo("Đoàn Khám Phá 2");
            assertThat(samplePassengers.get(2).getGroupName()).isEqualTo("Đoàn Khám Phá 3");
            assertThat(samplePassengers.get(3).getGroupName()).isEqualTo("Đoàn Khám Phá 1");
            assertThat(samplePassengers.get(4).getGroupName()).isEqualTo("Đoàn Khám Phá 2");
            assertThat(samplePassengers.get(5).getGroupName()).isEqualTo("Đoàn Khám Phá 3");

            // Không gán xe tự động khi autoAssignVehicles = false
            assertThat(samplePassengers.get(3).getBusNumber()).isNull();
        }

        @Test
        @DisplayName("Quy tắc 2: Tự động gán xe theo vòng tròn mã xe khả dụng khi autoAssignVehicles = true")
        void autoGenerate_AutoAssignVehicles_AssignsVehicleCodesRoundRobin() {
            AutoGenerateGroupsRequest request = AutoGenerateGroupsRequest.builder()
                    .targetGroupCount(2)
                    .groupPrefix("Nhóm")
                    .autoAssignVehicles(true)
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(samplePassengers);
            when(vehicleRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(sampleVehicles);
            when(guideRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(sampleGuides);

            excursionService.autoGeneratePassengerGroups(200L, request);

            // Xe 1: VEH-01, Xe 2: VEH-02 (2 xe cho 6 khách)
            assertThat(samplePassengers.get(0).getBusNumber()).isEqualTo("VEH-01");
            assertThat(samplePassengers.get(1).getBusNumber()).isEqualTo("VEH-02");
            assertThat(samplePassengers.get(2).getBusNumber()).isEqualTo("VEH-01");
            assertThat(samplePassengers.get(3).getBusNumber()).isEqualTo("VEH-02");
            assertThat(samplePassengers.get(4).getBusNumber()).isEqualTo("VEH-01");
            assertThat(samplePassengers.get(5).getBusNumber()).isEqualTo("VEH-02");
        }

        @Test
        @DisplayName("Quy tắc 3: Tự động tính số nhóm dựa theo số lượng xe khả dụng nếu không truyền targetGroupCount")
        void autoGenerate_DefaultsToAvailableVehiclesCount() {
            AutoGenerateGroupsRequest request = AutoGenerateGroupsRequest.builder()
                    .autoAssignVehicles(true)
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(samplePassengers);
            when(vehicleRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(sampleVehicles); // 2 xe
            when(guideRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(sampleGuides);

            excursionService.autoGeneratePassengerGroups(200L, request);

            // Có 2 xe -> sinh 2 nhóm: "Đoàn 1" và "Đoàn 2"
            assertThat(samplePassengers.get(0).getGroupName()).isEqualTo("Đoàn 1");
            assertThat(samplePassengers.get(1).getGroupName()).isEqualTo("Đoàn 2");
            assertThat(samplePassengers.get(2).getGroupName()).isEqualTo("Đoàn 1");
        }

        @Test
        @DisplayName("Quy tắc 4: Tự động tính số nhóm theo trần maxPassengersPerGroup khi không có xe khả dụng")
        void autoGenerate_NoVehicles_CalculatesGroupsUsingMaxPassengersPerGroup() {
            AutoGenerateGroupsRequest request = AutoGenerateGroupsRequest.builder()
                    .maxPassengersPerGroup(4) // 6 khách / 4 = 1.5 -> làm tròn lên thành 2 nhóm
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(samplePassengers);
            when(vehicleRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(Collections.emptyList());
            when(guideRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(Collections.emptyList());

            excursionService.autoGeneratePassengerGroups(200L, request);

            // 6 khách chia 2 nhóm
            assertThat(samplePassengers.get(0).getGroupName()).isEqualTo("Đoàn 1");
            assertThat(samplePassengers.get(1).getGroupName()).isEqualTo("Đoàn 2");
            assertThat(samplePassengers.get(2).getGroupName()).isEqualTo("Đoàn 1");
        }

        @Test
        @DisplayName("Quy tắc 5: Giới hạn số nhóm tối đa không vượt quá tổng số khách (targetGroups <= eligibleCount)")
        void autoGenerate_TargetGroupCountExceedsPassengers_CapsAtPassengerCount() {
            AutoGenerateGroupsRequest request = AutoGenerateGroupsRequest.builder()
                    .targetGroupCount(20) // Chỉ có 6 khách nhưng yêu cầu 20 nhóm
                    .groupPrefix("Group")
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(samplePassengers);
            when(vehicleRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(Collections.emptyList());
            when(guideRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(Collections.emptyList());

            excursionService.autoGeneratePassengerGroups(200L, request);

            // Tối đa 6 nhóm (mỗi khách 1 nhóm từ Group 1 đến Group 6)
            for (int i = 0; i < 6; i++) {
                assertThat(samplePassengers.get(i).getGroupName()).isEqualTo("Group " + (i + 1));
            }
        }
    }

    // =========================================================================
    // 4. QUY TẮC TOÀN VẸN SỨC CHỨA XE & HƯỚNG DẪN VIÊN (Logistics & Integrity Rules)
    // =========================================================================
    @Nested
    @DisplayName("4. Quy tắc toàn vẹn sức chứa xe và hướng dẫn viên (Capacity & Integrity Rules)")
    class LogisticsIntegrityRulesTests {

        @Test
        @DisplayName("Quy tắc sức chứa: Từ chối thêm xe khi assignedPassengers vượt quá sức chứa tối đa của xe")
        void addVehicle_AssignedExceedsCapacity_ThrowsBusinessRuleException() {
            AddExcursionVehicleRequest request = AddExcursionVehicleRequest.builder()
                    .licensePlate("14B-123.45")
                    .vehicleType("Xe 16 Chỗ")
                    .driverName("Lê Văn T")
                    .capacity(16)
                    .assignedPassengers(20) // 20 > 16
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));

            assertThatThrownBy(() -> excursionService.addVehicleToExcursion(200L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("không được vượt quá sức chứa phương tiện");

            verify(vehicleRepository, never()).save(any());
        }

        @Test
        @DisplayName("Quy tắc cập nhật xe: Từ chối cập nhật khi assignedPassengers âm hoặc vượt capacity mới")
        void updateVehicle_InvalidAssignedOrCapacity_ThrowsBusinessRuleException() {
            UpdateExcursionVehicleRequest requestNegative = UpdateExcursionVehicleRequest.builder()
                    .assignedPassengers(-1)
                    .build();

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(vehicleRepository.findByVehicleCodeAndExcursionIdAndActiveTrue("VEH-01", 200L))
                    .thenReturn(Optional.of(sampleVehicles.get(0)));

            assertThatThrownBy(() -> excursionService.updateVehicleInExcursion(200L, "VEH-01", requestNegative))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Số lượng hành khách đã gán không được nhỏ hơn 0");

            UpdateExcursionVehicleRequest requestExceed = UpdateExcursionVehicleRequest.builder()
                    .capacity(10) // giảm từ 29 xuống 10, trong khi assigned hiện tại là 3 (nhưng nếu request gán 15)
                    .assignedPassengers(15)
                    .build();

            assertThatThrownBy(() -> excursionService.updateVehicleInExcursion(200L, "VEH-01", requestExceed))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("không được vượt quá sức chứa phương tiện");
        }

        @Test
        @DisplayName("Quy tắc xóa hành khách: Giảm biến đếm currentParticipants của tour bờ và bảo lưu toàn vẹn")
        void removePassenger_ReducesCurrentParticipantsSafely() {
            mockExcursion.setCurrentParticipants(6);

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(1L, 200L))
                    .thenReturn(Optional.of(samplePassengers.get(0)));
            when(excursionRepository.save(any(ShoreExcursion.class))).thenAnswer(i -> i.getArgument(0));
            when(passengerRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(samplePassengers.subList(1, 6));

            ShoreExcursionResponse response = excursionService.removePassengerFromExcursion(200L, 1L);

            assertThat(response).isNotNull();
            assertThat(response.getCurrentParticipants()).isEqualTo(5);
            assertThat(samplePassengers.get(0).getActive()).isFalse();
            verify(passengerRepository).save(samplePassengers.get(0));
        }
    }

    // =========================================================================
    // 5. QUY TẮC THỐNG KÊ MANIFEST THEO ĐOÀN (Manifest Summary Metrics Rules)
    // =========================================================================
    @Nested
    @DisplayName("5. Quy tắc thống kê số liệu báo cáo đoàn (Manifest Summary Metrics Rules)")
    class ManifestSummaryMetricsRulesTests {

        @Test
        @DisplayName("Thống kê chính xác số lượng từng trạng thái: Check-in, On Tour, Safe Return, Pending")
        void manifest_AccuratelyAggregatesPassengerStatuses() {
            // Thiết lập trạng thái cụ thể cho các hành khách trong Đoàn A
            samplePassengers.get(0).setStatus(ExcursionPassengerStatus.CHECKED_IN);
            samplePassengers.get(1).setStatus(ExcursionPassengerStatus.ON_TOUR);
            samplePassengers.get(2).setStatus(ExcursionPassengerStatus.RETURNED_SAFE);

            when(excursionRepository.findById(200L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(samplePassengers);
            when(vehicleRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(sampleVehicles);
            when(guideRepository.findByExcursionIdAndActiveTrue(200L)).thenReturn(sampleGuides);

            ExcursionManifestSummaryResponse response = excursionService.getPassengerListsByGroup(200L);

            assertThat(response).isNotNull();
            assertThat(response.getTotalPassengers()).isEqualTo(6);

            ExcursionGroupManifestResponse groupA = response.getGroups().stream()
                    .filter(g -> "Đoàn A".equals(g.getGroupName()))
                    .findFirst()
                    .orElseThrow();

            assertThat(groupA.getTotalPassengers()).isEqualTo(3);
            assertThat(groupA.getCheckedInCount()).isEqualTo(1);
            assertThat(groupA.getOnTourCount()).isEqualTo(1);
            assertThat(groupA.getReturnedSafeCount()).isEqualTo(1);
            assertThat(groupA.getPendingCount()).isZero();

            // Khách chưa phân nhóm: 3 người đều REGISTERED -> pendingCount = 3
            ExcursionGroupManifestResponse unassigned = response.getGroups().stream()
                    .filter(g -> "Chưa phân nhóm".equals(g.getGroupName()))
                    .findFirst()
                    .orElseThrow();

            assertThat(unassigned.getTotalPassengers()).isEqualTo(3);
            assertThat(unassigned.getPendingCount()).isEqualTo(3);
        }
    }
}
