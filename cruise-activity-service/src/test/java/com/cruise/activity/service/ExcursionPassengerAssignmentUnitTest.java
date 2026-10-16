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
}
