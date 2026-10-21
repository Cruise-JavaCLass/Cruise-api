package com.cruise.activity.service;

import com.cruise.activity.dto.NotificationSummaryDto;
import com.cruise.activity.dto.PassengerNotificationDto;
import com.cruise.activity.dto.SendNotificationRequest;
import com.cruise.activity.entity.PassengerNotification;
import com.cruise.activity.enums.NotificationCategory;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.PassengerNotificationRepository;
import com.cruise.activity.repository.PassengerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PassengerNotificationServiceTest {

    @Mock
    private PassengerNotificationRepository notificationRepository;

    @Mock
    private PassengerRepository passengerRepository;

    @InjectMocks
    private PassengerNotificationService notificationService;

    private Long passengerId;
    private PassengerNotification notification1;
    private PassengerNotification notification2;

    @BeforeEach
    void setUp() {
        passengerId = 100L;
        notification1 = PassengerNotification.builder()
                .passengerId(passengerId)
                .title("All Aboard Alert")
                .message("Tàu sẽ xuất bến đúng 18:00")
                .category(NotificationCategory.CRITICAL)
                .isRead(false)
                .build();
        notification1.setId(1L);
        notification1.setCreatedAt(Instant.now());

        notification2 = PassengerNotification.builder()
                .passengerId(passengerId)
                .title("Spa Discount")
                .message("Giảm 20% tại Lotus Spa hôm nay")
                .category(NotificationCategory.PROMOTIONAL)
                .isRead(false)
                .build();
        notification2.setId(2L);
        notification2.setCreatedAt(Instant.now());
    }

    @Test
    @DisplayName("getNotifications: Lấy danh sách thông báo theo hành khách")
    void testGetNotifications_All() {
        when(passengerRepository.existsById(passengerId)).thenReturn(true);
        when(notificationRepository.findByPassengerIdOrderByCreatedAtDesc(passengerId))
                .thenReturn(List.of(notification1, notification2));

        List<PassengerNotificationDto> list = notificationService.getNotifications(passengerId, null);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getTitle()).isEqualTo("All Aboard Alert");
        assertThat(list.get(0).getCategory()).isEqualTo(NotificationCategory.CRITICAL);
    }

    @Test
    @DisplayName("getNotifications: Lọc theo Category CRITICAL")
    void testGetNotifications_ByCategory() {
        when(passengerRepository.existsById(passengerId)).thenReturn(true);
        when(notificationRepository.findByPassengerIdAndCategoryOrderByCreatedAtDesc(passengerId, NotificationCategory.CRITICAL))
                .thenReturn(List.of(notification1));

        List<PassengerNotificationDto> list = notificationService.getNotifications(passengerId, NotificationCategory.CRITICAL);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getCategory()).isEqualTo(NotificationCategory.CRITICAL);
    }

    @Test
    @DisplayName("getNotifications: Ném ngoại lệ khi Passenger không tồn tại")
    void testGetNotifications_PassengerNotFound() {
        when(passengerRepository.existsById(passengerId)).thenReturn(false);

        assertThatThrownBy(() -> notificationService.getNotifications(passengerId, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Passenger not found");
    }

    @Test
    @DisplayName("getNotificationSummary: Tính đúng số lượng chưa đọc tổng và theo loại")
    void testGetNotificationSummary() {
        com.cruise.activity.dto.CategoryUnreadCountProjection p1 = new com.cruise.activity.dto.CategoryUnreadCountProjection() {
            @Override public NotificationCategory getCategory() { return NotificationCategory.CRITICAL; }
            @Override public long getUnreadCount() { return 2L; }
        };
        com.cruise.activity.dto.CategoryUnreadCountProjection p2 = new com.cruise.activity.dto.CategoryUnreadCountProjection() {
            @Override public NotificationCategory getCategory() { return NotificationCategory.OPERATIONAL; }
            @Override public long getUnreadCount() { return 3L; }
        };

        when(passengerRepository.existsById(passengerId)).thenReturn(true);
        when(notificationRepository.countUnreadGroupedByCategory(passengerId)).thenReturn(List.of(p1, p2));

        NotificationSummaryDto summary = notificationService.getNotificationSummary(passengerId);

        assertThat(summary.getTotalUnread()).isEqualTo(5L);
        assertThat(summary.getCriticalUnread()).isEqualTo(2L);
        assertThat(summary.getUnreadByCategory()).containsEntry("CRITICAL", 2L);
        assertThat(summary.getUnreadByCategory()).containsEntry("OPERATIONAL", 3L);
        assertThat(summary.getUnreadByCategory()).containsEntry("REMINDER", 0L);
        assertThat(summary.getUnreadByCategory()).containsEntry("PROMOTIONAL", 0L);
    }

    @Test
    @DisplayName("markAsRead: Đánh dấu đã đọc thành công")
    void testMarkAsRead_Success() {
        when(passengerRepository.existsById(passengerId)).thenReturn(true);
        when(notificationRepository.findByIdAndPassengerId(1L, passengerId)).thenReturn(Optional.of(notification1));
        when(notificationRepository.save(any(PassengerNotification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PassengerNotificationDto result = notificationService.markAsRead(passengerId, 1L);

        assertThat(result.getIsRead()).isTrue();
        assertThat(result.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("markAsRead: Ném ResourceNotFoundException khi không tìm thấy thông báo")
    void testMarkAsRead_NotFound() {
        when(passengerRepository.existsById(passengerId)).thenReturn(true);
        when(notificationRepository.findByIdAndPassengerId(999L, passengerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(passengerId, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Notification not found");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("markAllAsRead: Cập nhật toàn bộ thông báo của hành khách thành đã đọc")
    void testMarkAllAsRead_Success() {
        when(passengerRepository.existsById(passengerId)).thenReturn(true);
        when(notificationRepository.markAllAsRead(eq(passengerId), any(Instant.class))).thenReturn(3);

        notificationService.markAllAsRead(passengerId);

        verify(notificationRepository).markAllAsRead(eq(passengerId), any(Instant.class));
    }

    @Test
    @DisplayName("sendNotification: Phát thông báo mới thành công")
    void testSendNotification_Success() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .title("Đổi giờ tập trung")
                .message("Tập trung tại sảnh Deck 4 trước 15:30")
                .category(NotificationCategory.OPERATIONAL)
                .actionUrl("/my-schedule")
                .build();

        when(passengerRepository.existsById(passengerId)).thenReturn(true);
        when(notificationRepository.save(any(PassengerNotification.class))).thenAnswer(invocation -> {
            PassengerNotification n = invocation.getArgument(0);
            n.setId(99L);
            n.setCreatedAt(Instant.now());
            return n;
        });

        PassengerNotificationDto saved = notificationService.sendNotification(passengerId, request);

        assertThat(saved.getId()).isEqualTo(99L);
        assertThat(saved.getTitle()).isEqualTo("Đổi giờ tập trung");
        assertThat(saved.getCategory()).isEqualTo(NotificationCategory.OPERATIONAL);
        assertThat(saved.getIsRead()).isFalse();
    }
}
