package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.ItineraryChangeNotificationDto;
import com.cruise.itinerary.enums.ChangeType;
import com.cruise.itinerary.event.ItineraryChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItineraryNotificationTriggerService Unit Tests (KAN-126)")
class ItineraryNotificationTriggerServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ItineraryNotificationTriggerService notificationTriggerService;

    @BeforeEach
    void setUp() {
        notificationTriggerService.clearHistory();
    }

    @Test
    @DisplayName("triggerChangeNotification - Phát event thành công qua ApplicationEventPublisher")
    void triggerChangeNotificationPublishesEvent() {
        ItineraryChangedEvent event = ItineraryChangedEvent.builder()
                .tourId(1L)
                .tourCode("TOUR-001")
                .tourName("Vịnh Hạ Long")
                .changeType(ChangeType.UPDATE)
                .title("Điều chỉnh cảng")
                .reason("Thời tiết xấu")
                .build();

        notificationTriggerService.triggerChangeNotification(event);

        ArgumentCaptor<ItineraryChangedEvent> captor = ArgumentCaptor.forClass(ItineraryChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());

        ItineraryChangedEvent captured = captor.getValue();
        assertThat(captured.getTourId()).isEqualTo(1L);
        assertThat(captured.getTourCode()).isEqualTo("TOUR-001");
        assertThat(captured.getChangeType()).isEqualTo(ChangeType.UPDATE);
    }

    @Test
    @DisplayName("onItineraryChanged - Tạo thông báo với độ khẩn URGENT cho sự kiện UPDATE")
    void onItineraryChangedCreatesUrgentNotificationForUpdate() {
        ItineraryChangedEvent event = ItineraryChangedEvent.builder()
                .tourId(10L)
                .tourCode("TOUR-010")
                .tourName("Hải Trình Kỳ Quan")
                .dayNumber(2)
                .changeType(ChangeType.UPDATE)
                .title("Đổi giờ khởi hành")
                .reason("Cập nhật theo luồng hàng hải")
                .previousState("08:00")
                .newState("09:00")
                .build();

        notificationTriggerService.onItineraryChanged(event);

        List<ItineraryChangeNotificationDto> notifications = notificationTriggerService.getNotificationsByTourId(10L);
        assertThat(notifications).hasSize(1);

        ItineraryChangeNotificationDto notif = notifications.get(0);
        assertThat(notif.getUrgency()).isEqualTo("URGENT");
        assertThat(notif.getSubject()).contains("Hải Trình Kỳ Quan - Ngày 2");
        assertThat(notif.getMessage()).contains("Loại thay đổi: UPDATE");
        assertThat(notif.getMessage()).contains("Đổi giờ khởi hành");
        assertThat(notif.getMessage()).contains("'08:00' -> '09:00'");
    }

    @Test
    @DisplayName("onItineraryChanged - Tạo thông báo với độ khẩn CRITICAL cho sự kiện DELETE")
    void onItineraryChangedCreatesCriticalNotificationForDelete() {
        ItineraryChangedEvent event = ItineraryChangedEvent.builder()
                .tourId(20L)
                .tourCode("TOUR-020")
                .tourName("Đà Nẵng Cruise")
                .dayNumber(3)
                .changeType(ChangeType.DELETE)
                .title("Hủy chuyến tham quan")
                .reason("Sóng lớn")
                .build();

        notificationTriggerService.onItineraryChanged(event);

        List<ItineraryChangeNotificationDto> notifications = notificationTriggerService.getNotificationsByTourId(20L);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getUrgency()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("onItineraryChanged - Tạo thông báo với độ khẩn NORMAL cho sự kiện CREATE")
    void onItineraryChangedCreatesNormalNotificationForCreate() {
        ItineraryChangedEvent event = ItineraryChangedEvent.builder()
                .tourId(30L)
                .tourCode("TOUR-030")
                .tourName("Nha Trang Cruise")
                .dayNumber(1)
                .changeType(ChangeType.CREATE)
                .title("Tạo mới lịch trình ngày 1")
                .build();

        notificationTriggerService.onItineraryChanged(event);

        List<ItineraryChangeNotificationDto> notifications = notificationTriggerService.getNotificationsByTourId(30L);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getUrgency()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("getAllRecentNotifications và clearHistory hoạt động chính xác")
    void getAllRecentNotificationsAndClear() {
        ItineraryChangedEvent event = ItineraryChangedEvent.builder()
                .tourId(1L)
                .tourCode("TOUR-001")
                .changeType(ChangeType.REORDER)
                .build();

        notificationTriggerService.onItineraryChanged(event);
        assertThat(notificationTriggerService.getAllRecentNotifications()).hasSize(1);

        notificationTriggerService.clearHistory();
        assertThat(notificationTriggerService.getAllRecentNotifications()).isEmpty();
    }
}
