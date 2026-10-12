package com.cruise.itinerary.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.itinerary.enums.ChangeType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Thực thể Lưu vết Lịch sử thay đổi lịch trình hải trình (Itinerary Change History - KAN-124)
 */
@Entity
@Table(name = "itinerary_change_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryChangeHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_id", nullable = false)
    private CruiseTour tour;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_itinerary_id")
    private DailyItinerary dailyItinerary;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 30)
    private ChangeType changeType;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @Column(name = "previous_state", length = 3000)
    private String previousState;

    @Column(name = "new_state", length = 3000)
    private String newState;
}
