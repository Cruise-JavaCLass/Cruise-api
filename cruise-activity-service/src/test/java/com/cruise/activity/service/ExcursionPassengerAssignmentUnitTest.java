package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.activity.enums.ExcursionStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.ExcursionPassengerRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Excursion Passenger Assignment Unit Tests (KAN-176)")
class ExcursionPassengerAssignmentUnitTest {

    @Mock
    private ShoreExcursionRepository excursionRepository;

    @Mock
    private ExcursionPassengerRepository passengerRepository;

    @InjectMocks
    private ShoreExcursionService excursionService;

    private ShoreExcursion mockExcursion;
    private ExcursionPassenger mockPassenger;

    @BeforeEach
    void setUp() {
        mockExcursion = ShoreExcursion.builder()
                .tourId(1L)
                .portId(10L)
                .dayNumber(2)
                .title("Khám phá Hang Sửng Sốt")
                .portName("Cảng Tuần Châu")
                .meetingPoint("Cầu tàu 1")
                .excursionDate(LocalDate.now().plusDays(5))
                .gatheringTime(LocalTime.of(8, 0))
                .departureTime(LocalTime.of(8, 30))
                .returnToShipTime(LocalTime.of(12, 0))
                .maxCapacity(30)
                .currentParticipants(2)
                .price(BigDecimal.valueOf(500000))
                .childPrice(BigDecimal.valueOf(250000))
                .currency("VND")
                .status(ExcursionStatus.SCHEDULED)
                .active(true)
                .build();
        mockExcursion.setId(100L);

        mockPassenger = ExcursionPassenger.builder()
                .excursionId(100L)
                .passengerName("Nguyễn Văn A")
                .cabinNumber("Suite-801")
                .ticketCode("EXC-TKT-100-001")
                .groupName("Đoàn A - Khám phá")
                .busNumber("XE-01 (45 Chỗ)")
                .emergencyPhone("+84 912 345 678")
                .status(ExcursionPassengerStatus.REGISTERED)
                .active(true)
                .build();
        mockPassenger.setId(1L);
    }

    @Nested
    @DisplayName("1. Thêm hành khách vào tour bờ (addPassengerToExcursion)")
    class AddPassengerTests {

        @Test
        @DisplayName("Thêm hành khách thành công, tăng số lượng tham gia và tự sinh mã vé khi để trống")
        void addPassenger_Success_AutoGeneratesTicketCode() {
            AddExcursionPassengerRequest request = AddExcursionPassengerRequest.builder()
                    .passengerName("Trần Thị Mai")
                    .cabinNumber("Balcony-504")
                    .emergencyPhone("+84 988 765 432")
                    .build();

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.countByExcursionIdAndActiveTrue(100L)).thenReturn(2L);
            when(excursionRepository.save(any(ShoreExcursion.class))).thenAnswer(inv -> inv.getArgument(0));
            when(passengerRepository.save(any(ExcursionPassenger.class))).thenAnswer(inv -> inv.getArgument(0));
            when(passengerRepository.findByExcursionIdAndActiveTrue(100L)).thenReturn(List.of(mockPassenger));

            ShoreExcursionResponse response = excursionService.addPassengerToExcursion(100L, request);

            assertThat(response).isNotNull();
            assertThat(response.getCurrentParticipants()).isEqualTo(3);
            verify(passengerRepository).save(argThat(p ->
                    p.getTicketCode().startsWith("EXC-TKT-100-") &&
                    p.getPassengerName().equals("Trần Thị Mai") &&
                    p.getStatus() == ExcursionPassengerStatus.REGISTERED
            ));
        }

        @Test
        @DisplayName("Ném lỗi khi tour bờ đã đạt sức chứa tối đa")
        void addPassenger_ExceedCapacity_ThrowsBusinessRuleException() {
            mockExcursion.setCurrentParticipants(30);
            mockExcursion.setMaxCapacity(30);

            AddExcursionPassengerRequest request = AddExcursionPassengerRequest.builder()
                    .passengerName("Khách Dư")
                    .cabinNumber("Interior-101")
                    .build();

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));

            assertThatThrownBy(() -> excursionService.addPassengerToExcursion(100L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Tour đã đạt sức chứa tối đa (30 khách)");

            verify(passengerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Ném lỗi khi tour bờ đã bị hủy (CANCELLED)")
        void addPassenger_CancelledTour_ThrowsBusinessRuleException() {
            mockExcursion.setStatus(ExcursionStatus.CANCELLED);

            AddExcursionPassengerRequest request = AddExcursionPassengerRequest.builder()
                    .passengerName("Lê Văn B")
                    .cabinNumber("Suite-802")
                    .build();

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));

            assertThatThrownBy(() -> excursionService.addPassengerToExcursion(100L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Không thể thêm hành khách vào tour bờ đã hủy hoặc đã hoàn thành");
        }

        @Test
        @DisplayName("Ném lỗi khi mã vé chỉ định đã tồn tại trên hệ thống")
        void addPassenger_DuplicateTicketCode_ThrowsBusinessRuleException() {
            AddExcursionPassengerRequest request = AddExcursionPassengerRequest.builder()
                    .passengerName("Phạm Văn C")
                    .cabinNumber("Balcony-301")
                    .ticketCode("EXC-TKT-DUPLICATE")
                    .build();

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.existsByTicketCode("EXC-TKT-DUPLICATE")).thenReturn(true);

            assertThatThrownBy(() -> excursionService.addPassengerToExcursion(100L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Mã vé tour bờ 'EXC-TKT-DUPLICATE' đã tồn tại");
        }
    }

    @Nested
    @DisplayName("2. Gán đoàn và xe cho từng hành khách (assignPassengerToGroup)")
    class AssignSinglePassengerGroupTests {

        @Test
        @DisplayName("Gán đoàn và xe thành công cho hành khách hợp lệ")
        void assignPassengerToGroup_Success() {
            AssignPassengerGroupRequest request = AssignPassengerGroupRequest.builder()
                    .groupName("Đoàn B - Ẩm thực")
                    .busNumber("XE-02 (29 Chỗ)")
                    .build();

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(1L, 100L))
                    .thenReturn(Optional.of(mockPassenger));
            when(passengerRepository.findByExcursionIdAndActiveTrue(100L)).thenReturn(List.of(mockPassenger));

            ShoreExcursionResponse response = excursionService.assignPassengerToGroup(100L, 1L, request);

            assertThat(response).isNotNull();
            assertThat(mockPassenger.getGroupName()).isEqualTo("Đoàn B - Ẩm thực");
            assertThat(mockPassenger.getBusNumber()).isEqualTo("XE-02 (29 Chỗ)");
            verify(passengerRepository).save(mockPassenger);
        }

        @Test
        @DisplayName("Ném lỗi ResourceNotFoundException khi hành khách không tồn tại trong tour bờ")
        void assignPassengerToGroup_NotFound_ThrowsException() {
            AssignPassengerGroupRequest request = AssignPassengerGroupRequest.builder()
                    .groupName("Đoàn VIP")
                    .busNumber("BUS-VIP-01")
                    .build();

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(999L, 100L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> excursionService.assignPassengerToGroup(100L, 999L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy hành khách với ID: 999 trong tour bờ ID: 100");
        }
    }

    @Nested
    @DisplayName("3. Gán đoàn và xe hàng loạt (bulkAssignPassengersToGroup)")
    class BulkAssignPassengersGroupTests {

        @Test
        @DisplayName("Gán đoàn và xe hàng loạt thành công cho danh sách hành khách")
        void bulkAssignPassengersToGroup_Success() {
            ExcursionPassenger p2 = ExcursionPassenger.builder()
                    .excursionId(100L)
                    .passengerName("Hoàng Văn D")
                    .cabinNumber("Ocean-202")
                    .ticketCode("EXC-TKT-100-002")
                    .active(true)
                    .build();
            p2.setId(2L);

            BulkAssignPassengerGroupRequest request = BulkAssignPassengerGroupRequest.builder()
                    .passengerIds(List.of(1L, 2L))
                    .groupName("Đoàn C - Thưởng ngoạn")
                    .busNumber("XE-03 (45 Chỗ)")
                    .build();

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndIdInAndActiveTrue(100L, List.of(1L, 2L)))
                    .thenReturn(List.of(mockPassenger, p2));
            when(passengerRepository.findByExcursionIdAndActiveTrue(100L)).thenReturn(List.of(mockPassenger, p2));

            ShoreExcursionResponse response = excursionService.bulkAssignPassengersToGroup(100L, request);

            assertThat(response).isNotNull();
            assertThat(mockPassenger.getGroupName()).isEqualTo("Đoàn C - Thưởng ngoạn");
            assertThat(mockPassenger.getBusNumber()).isEqualTo("XE-03 (45 Chỗ)");
            assertThat(p2.getGroupName()).isEqualTo("Đoàn C - Thưởng ngoạn");
            assertThat(p2.getBusNumber()).isEqualTo("XE-03 (45 Chỗ)");
            verify(passengerRepository).saveAll(List.of(mockPassenger, p2));
        }

        @Test
        @DisplayName("Ném lỗi BusinessRuleException khi không có hành khách hợp lệ nào khớp với danh sách ID")
        void bulkAssignPassengersToGroup_EmptyMatch_ThrowsException() {
            BulkAssignPassengerGroupRequest request = BulkAssignPassengerGroupRequest.builder()
                    .passengerIds(List.of(991L, 992L))
                    .groupName("Đoàn A")
                    .busNumber("XE-01")
                    .build();

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndIdInAndActiveTrue(100L, List.of(991L, 992L)))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> excursionService.bulkAssignPassengersToGroup(100L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Không tìm thấy hành khách hợp lệ nào trong tour bờ để gán nhóm");
        }
    }

    @Nested
    @DisplayName("4. Xóa hành khách khỏi tour bờ (removePassengerFromExcursion)")
    class RemovePassengerTests {

        @Test
        @DisplayName("Xóa hành khách thành công, chuyển active=false và giảm currentParticipants")
        void removePassenger_Success() {
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(1L, 100L))
                    .thenReturn(Optional.of(mockPassenger));
            when(excursionRepository.save(any(ShoreExcursion.class))).thenAnswer(inv -> inv.getArgument(0));
            when(passengerRepository.findByExcursionIdAndActiveTrue(100L)).thenReturn(Collections.emptyList());

            ShoreExcursionResponse response = excursionService.removePassengerFromExcursion(100L, 1L);

            assertThat(response).isNotNull();
            assertThat(response.getCurrentParticipants()).isEqualTo(1); // 2 - 1
            assertThat(mockPassenger.getActive()).isFalse();
            verify(passengerRepository).save(mockPassenger);
        }

        @Test
        @DisplayName("Ném lỗi ResourceNotFoundException khi xóa hành khách không tồn tại")
        void removePassenger_NotFound_ThrowsException() {
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(555L, 100L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> excursionService.removePassengerFromExcursion(100L, 555L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy hành khách với ID: 555 trong tour bờ ID: 100");
        }
    }

    @Nested
    @DisplayName("5. Tra cứu danh sách hành khách theo tour bờ (getPassengersByExcursion)")
    class GetPassengersTests {

        @Test
        @DisplayName("Tra cứu thành công danh sách hành khách đang active của tour bờ")
        void getPassengersByExcursion_Success() {
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(100L)).thenReturn(List.of(mockPassenger));

            List<ExcursionPassengerResponse> list = excursionService.getPassengersByExcursion(100L);

            assertThat(list).hasSize(1);
            assertThat(list.get(0).getPassengerName()).isEqualTo("Nguyễn Văn A");
            assertThat(list.get(0).getTicketCode()).isEqualTo("EXC-TKT-100-001");
        }
    }

    @Nested
    @DisplayName("6. Điểm danh và Quét vé hành khách tour bờ (KAN-226)")
    class AttendanceTests {

        @Test
        @DisplayName("checkInPassenger - Điểm danh thành công qua Ticket Code")
        void checkInPassenger_TicketCode_Success() {
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndTicketCodeAndActiveTrue(100L, "EXC-TKT-100-001"))
                    .thenReturn(Optional.of(mockPassenger));

            ExcursionCheckInRequest req = ExcursionCheckInRequest.builder()
                    .ticketCode("EXC-TKT-100-001")
                    .targetStatus(ExcursionPassengerStatus.CHECKED_IN)
                    .build();

            ExcursionCheckInResponse res = excursionService.checkInPassenger(100L, req);

            assertThat(res).isNotNull();
            assertThat(res.getCurrentStatus()).isEqualTo(ExcursionPassengerStatus.CHECKED_IN);
            assertThat(mockPassenger.getStatus()).isEqualTo(ExcursionPassengerStatus.CHECKED_IN);
            assertThat(mockPassenger.getCheckedInAt()).isNotNull();
            verify(passengerRepository, times(1)).save(mockPassenger);
        }

        @Test
        @DisplayName("checkInPassenger - Xác nhận quay lại tàu an toàn (RETURNED_SAFE)")
        void checkInPassenger_ReturnedSafe_Success() {
            mockPassenger.setStatus(ExcursionPassengerStatus.ON_TOUR);
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(1L, 100L))
                    .thenReturn(Optional.of(mockPassenger));

            ExcursionCheckInRequest req = ExcursionCheckInRequest.builder()
                    .excursionPassengerId(1L)
                    .targetStatus(ExcursionPassengerStatus.RETURNED_SAFE)
                    .build();

            ExcursionCheckInResponse res = excursionService.checkInPassenger(100L, req);

            assertThat(res.getCurrentStatus()).isEqualTo(ExcursionPassengerStatus.RETURNED_SAFE);
            assertThat(mockPassenger.getReturnedSafeAt()).isNotNull();
        }

        @Test
        @DisplayName("checkInPassenger - Báo lỗi khi chuyển trạng thái trùng lặp")
        void checkInPassenger_DuplicateStatus_ThrowsException() {
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(1L, 100L))
                    .thenReturn(Optional.of(mockPassenger));

            ExcursionCheckInRequest req = ExcursionCheckInRequest.builder()
                    .excursionPassengerId(1L)
                    .targetStatus(ExcursionPassengerStatus.REGISTERED)
                    .build();

            assertThatThrownBy(() -> excursionService.checkInPassenger(100L, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("đã ở trạng thái REGISTERED");
        }

        @Test
        @DisplayName("checkInPassenger - Điểm danh thành công qua Thẻ RFID")
        void checkInPassenger_RfidTag_Success() {
            mockPassenger.setRfidTag("RFID-001");
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndRfidTagAndActiveTrue(100L, "RFID-001"))
                    .thenReturn(Optional.of(mockPassenger));

            ExcursionCheckInRequest req = ExcursionCheckInRequest.builder()
                    .rfidTag("RFID-001")
                    .targetStatus(ExcursionPassengerStatus.CHECKED_IN)
                    .build();

            ExcursionCheckInResponse res = excursionService.checkInPassenger(100L, req);

            assertThat(res).isNotNull();
            assertThat(res.getCurrentStatus()).isEqualTo(ExcursionPassengerStatus.CHECKED_IN);
            verify(passengerRepository, times(1)).save(mockPassenger);
        }

        @Test
        @DisplayName("checkInPassenger - Điểm danh thành công qua Số Cabin")
        void checkInPassenger_CabinNumber_Success() {
            mockPassenger.setCabinNumber("CAB-101");
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndCabinNumberAndActiveTrue(100L, "CAB-101"))
                    .thenReturn(List.of(mockPassenger));

            ExcursionCheckInRequest req = ExcursionCheckInRequest.builder()
                    .cabinNumber("CAB-101")
                    .targetStatus(ExcursionPassengerStatus.CHECKED_IN)
                    .build();

            ExcursionCheckInResponse res = excursionService.checkInPassenger(100L, req);

            assertThat(res).isNotNull();
            assertThat(res.getCurrentStatus()).isEqualTo(ExcursionPassengerStatus.CHECKED_IN);
            verify(passengerRepository, times(1)).save(mockPassenger);
        }

        @Test
        @DisplayName("checkInPassenger - Cabin có nhiều khách cùng tour từ chối do không rõ ràng (Ambiguous)")
        void checkInPassenger_CabinNumber_AmbiguousMultiplePassengers_ThrowsException() {
            mockPassenger.setCabinNumber("CAB-101");
            ExcursionPassenger passenger2 = ExcursionPassenger.builder()
                    .excursionId(100L)
                    .cabinNumber("CAB-101")
                    .passengerName("Người đi cùng")
                    .build();
            passenger2.setId(3L);

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndCabinNumberAndActiveTrue(100L, "CAB-101"))
                    .thenReturn(List.of(mockPassenger, passenger2));

            ExcursionCheckInRequest req = ExcursionCheckInRequest.builder()
                    .cabinNumber("CAB-101")
                    .targetStatus(ExcursionPassengerStatus.CHECKED_IN)
                    .build();

            assertThatThrownBy(() -> excursionService.checkInPassenger(100L, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Vui lòng quét Mã vé hoặc Thẻ RFID riêng");
        }

        @Test
        @DisplayName("checkInPassenger - Cabin không có khách thuộc tour này báo ResourceNotFoundException")
        void checkInPassenger_CabinNumber_NotMatchingExcursion_ThrowsException() {
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndCabinNumberAndActiveTrue(100L, "CAB-101"))
                    .thenReturn(List.of());

            ExcursionCheckInRequest req = ExcursionCheckInRequest.builder()
                    .cabinNumber("CAB-101")
                    .targetStatus(ExcursionPassengerStatus.CHECKED_IN)
                    .build();

            assertThatThrownBy(() -> excursionService.checkInPassenger(100L, req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("không có hành khách đăng ký tour bờ");
        }

        @Test
        @DisplayName("checkInPassenger - Báo lỗi khi không cung cấp bất kỳ thông tin nhận diện nào")
        void checkInPassenger_NoIdentifier_ThrowsException() {
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));

            ExcursionCheckInRequest req = ExcursionCheckInRequest.builder()
                    .targetStatus(ExcursionPassengerStatus.CHECKED_IN)
                    .build();

            assertThatThrownBy(() -> excursionService.checkInPassenger(100L, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Vui lòng cung cấp ít nhất một thông tin nhận diện");
        }

        @Test
        @DisplayName("checkInPassenger - Không cho phép đổi trạng thái của khách đã RETURNED_SAFE")
        void checkInPassenger_AlreadyReturnedSafe_ThrowsException() {
            mockPassenger.setStatus(ExcursionPassengerStatus.RETURNED_SAFE);
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(1L, 100L))
                    .thenReturn(Optional.of(mockPassenger));

            ExcursionCheckInRequest req = ExcursionCheckInRequest.builder()
                    .excursionPassengerId(1L)
                    .targetStatus(ExcursionPassengerStatus.CHECKED_IN)
                    .build();

            assertThatThrownBy(() -> excursionService.checkInPassenger(100L, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("đã hoàn tất tour và về tàu an toàn");
        }

        @Test
        @DisplayName("bulkCheckInPassengers - Điểm danh hàng loạt theo danh sách ID thành công")
        void bulkCheckInPassengers_ByIds_Success() {
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndIdInAndActiveTrue(100L, List.of(1L)))
                    .thenReturn(List.of(mockPassenger));

            BulkExcursionAttendanceRequest req = BulkExcursionAttendanceRequest.builder()
                    .passengerIds(List.of(1L))
                    .targetStatus(ExcursionPassengerStatus.CHECKED_IN)
                    .build();

            BulkExcursionAttendanceResponse res = excursionService.bulkCheckInPassengers(100L, req);

            assertThat(res.getTotalRequested()).isEqualTo(1);
            assertThat(res.getTotalUpdated()).isEqualTo(1);
            assertThat(res.getUpdatedPassengerIds()).contains(1L);
        }

        @Test
        @DisplayName("bulkCheckInPassengers - Tự động loại bỏ ID trùng lặp trong danh sách yêu cầu")
        void bulkCheckInPassengers_ByIds_DuplicateIds_Success() {
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndIdInAndActiveTrue(100L, List.of(1L)))
                    .thenReturn(List.of(mockPassenger));

            BulkExcursionAttendanceRequest req = BulkExcursionAttendanceRequest.builder()
                    .passengerIds(List.of(1L, 1L))
                    .targetStatus(ExcursionPassengerStatus.CHECKED_IN)
                    .build();

            BulkExcursionAttendanceResponse res = excursionService.bulkCheckInPassengers(100L, req);

            assertThat(res.getTotalRequested()).isEqualTo(1);
            assertThat(res.getTotalUpdated()).isEqualTo(1);
            assertThat(res.getUpdatedPassengerIds()).contains(1L);
        }

        @Test
        @DisplayName("bulkCheckInPassengers - Báo lỗi khi danh sách ID có chứa ID không tồn tại hoặc inactive")
        void bulkCheckInPassengers_ByIds_MissingId_ThrowsException() {
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndIdInAndActiveTrue(100L, List.of(1L, 999L)))
                    .thenReturn(List.of(mockPassenger));

            BulkExcursionAttendanceRequest req = BulkExcursionAttendanceRequest.builder()
                    .passengerIds(List.of(1L, 999L))
                    .targetStatus(ExcursionPassengerStatus.CHECKED_IN)
                    .build();

            assertThatThrownBy(() -> excursionService.bulkCheckInPassengers(100L, req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy các hành khách có ID: [999]");
        }

        @Test
        @DisplayName("checkInPassenger - Báo lỗi khi nhảy cóc thứ tự điểm danh (REGISTERED sang RETURNED_SAFE)")
        void checkInPassenger_InvalidSequence_ThrowsException() {
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByIdAndExcursionIdAndActiveTrue(1L, 100L))
                    .thenReturn(Optional.of(mockPassenger));

            ExcursionCheckInRequest req = ExcursionCheckInRequest.builder()
                    .excursionPassengerId(1L)
                    .targetStatus(ExcursionPassengerStatus.RETURNED_SAFE)
                    .build();

            assertThatThrownBy(() -> excursionService.checkInPassenger(100L, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Không thể chuyển trạng thái của hành khách");
        }

        @Test
        @DisplayName("bulkCheckInPassengers - Điểm danh hàng loạt theo tên đoàn và cập nhật RETURNED_SAFE")
        void bulkCheckInPassengers_ByGroupName_ReturnSafe_Success() {
            mockPassenger.setStatus(ExcursionPassengerStatus.ON_TOUR);
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndGroupNameAndActiveTrue(100L, "Đoàn VIP"))
                    .thenReturn(List.of(mockPassenger));

            BulkExcursionAttendanceRequest req = BulkExcursionAttendanceRequest.builder()
                    .groupName("Đoàn VIP")
                    .targetStatus(ExcursionPassengerStatus.RETURNED_SAFE)
                    .build();

            BulkExcursionAttendanceResponse res = excursionService.bulkCheckInPassengers(100L, req);

            assertThat(res.getTotalRequested()).isEqualTo(1);
            assertThat(res.getTotalUpdated()).isEqualTo(1);
            assertThat(mockPassenger.getStatus()).isEqualTo(ExcursionPassengerStatus.RETURNED_SAFE);
            assertThat(mockPassenger.getReturnedSafeAt()).isNotNull();
        }

        @Test
        @DisplayName("bulkCheckInPassengers - Báo lỗi khi không truyền thông tin lọc")
        void bulkCheckInPassengers_NoFilter_ThrowsException() {
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));

            BulkExcursionAttendanceRequest req = BulkExcursionAttendanceRequest.builder()
                    .targetStatus(ExcursionPassengerStatus.CHECKED_IN)
                    .build();

            assertThatThrownBy(() -> excursionService.bulkCheckInPassengers(100L, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Vui lòng cung cấp danh sách ID");
        }

        @Test
        @DisplayName("bulkCheckInPassengers - Báo lỗi khi danh sách kết quả lọc rỗng")
        void bulkCheckInPassengers_EmptyResult_ThrowsException() {
            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndBusNumberAndActiveTrue(100L, "XE-99"))
                    .thenReturn(List.of());

            BulkExcursionAttendanceRequest req = BulkExcursionAttendanceRequest.builder()
                    .busNumber("XE-99")
                    .targetStatus(ExcursionPassengerStatus.CHECKED_IN)
                    .build();

            assertThatThrownBy(() -> excursionService.bulkCheckInPassengers(100L, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Không tìm thấy hành khách nào");
        }

        @Test
        @DisplayName("bulkCheckInPassengers - Bỏ qua hành khách không đủ điều kiện chuyển trạng thái và báo cáo skippedIds")
        void bulkCheckInPassengers_SkippedIneligiblePassengers() {
            ExcursionPassenger pEligible = ExcursionPassenger.builder()
                    .excursionId(100L).passengerName("Khách Hợp Lệ")
                    .busNumber("XE-01").status(ExcursionPassengerStatus.ON_TOUR).build();
            pEligible.setId(10L);
            ExcursionPassenger pIneligible = ExcursionPassenger.builder()
                    .excursionId(100L).passengerName("Khách Chưa Đi Tour")
                    .busNumber("XE-01").status(ExcursionPassengerStatus.REGISTERED).build();
            pIneligible.setId(11L);

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndBusNumberAndActiveTrue(100L, "XE-01"))
                    .thenReturn(List.of(pEligible, pIneligible));

            BulkExcursionAttendanceRequest req = BulkExcursionAttendanceRequest.builder()
                    .busNumber("XE-01")
                    .targetStatus(ExcursionPassengerStatus.RETURNED_SAFE)
                    .build();

            BulkExcursionAttendanceResponse res = excursionService.bulkCheckInPassengers(100L, req);

            assertThat(res.getTotalRequested()).isEqualTo(2);
            assertThat(res.getTotalUpdated()).isEqualTo(1);
            assertThat(res.getUpdatedPassengerIds()).contains(10L);
            assertThat(res.getSkippedPassengerIds()).contains(11L);
            assertThat(pEligible.getStatus()).isEqualTo(ExcursionPassengerStatus.RETURNED_SAFE);
            assertThat(pIneligible.getStatus()).isEqualTo(ExcursionPassengerStatus.REGISTERED);
        }

        @Test
        @DisplayName("getAttendanceSummary - Khách REGISTERED chưa đi tour không bị tính là Missing")
        void getAttendanceSummary_RegisteredPassengersNotCountedAsMissing() {
            ExcursionPassenger pRegistered = ExcursionPassenger.builder()
                    .excursionId(100L).passengerName("Khách Chưa Xuất Bến")
                    .busNumber("XE-01").groupName("Group 1")
                    .status(ExcursionPassengerStatus.REGISTERED).build();
            pRegistered.setId(1L);

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(100L))
                    .thenReturn(List.of(pRegistered));

            ExcursionAttendanceSummaryResponse summary = excursionService.getAttendanceSummary(100L);

            assertThat(summary).isNotNull();
            assertThat(summary.getTotalRegistered()).isEqualTo(1);
            assertThat(summary.getTotalMissingReturn()).isZero();
            assertThat(summary.getMissingPassengers()).isEmpty();
            assertThat(summary.getTotalPendingDeparture()).isEqualTo(1);
        }

        @Test
        @DisplayName("getAttendanceSummary - Thống kê với đầy đủ khách ON_TOUR và RETURNED_SAFE")
        void getAttendanceSummary_WithReturnedSafeAndOnTour() {
            ExcursionPassenger p1 = ExcursionPassenger.builder()
                    .excursionId(100L).passengerName("Khách 1")
                    .busNumber("XE-01").groupName("Group 1")
                    .status(ExcursionPassengerStatus.RETURNED_SAFE).build();
            p1.setId(1L);
            ExcursionPassenger p2 = ExcursionPassenger.builder()
                    .excursionId(100L).passengerName("Khách 2")
                    .busNumber("XE-01").groupName("Group 1")
                    .status(ExcursionPassengerStatus.ON_TOUR).build();
            p2.setId(2L);

            when(excursionRepository.findById(100L)).thenReturn(Optional.of(mockExcursion));
            when(passengerRepository.findByExcursionIdAndActiveTrue(100L))
                    .thenReturn(List.of(p1, p2));

            ExcursionAttendanceSummaryResponse summary = excursionService.getAttendanceSummary(100L);

            assertThat(summary).isNotNull();
            assertThat(summary.getTotalRegistered()).isEqualTo(2);
            assertThat(summary.getTotalReturnedSafe()).isEqualTo(1);
            assertThat(summary.getTotalOnTour()).isEqualTo(1);
            assertThat(summary.getTotalMissingReturn()).isEqualTo(1);
            assertThat(summary.getMissingPassengers()).hasSize(1);
            assertThat(summary.getMissingPassengers().get(0).getPassengerName()).isEqualTo("Khách 2");
        }
    }
}
