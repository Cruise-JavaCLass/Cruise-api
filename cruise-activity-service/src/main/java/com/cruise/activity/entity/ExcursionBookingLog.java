package com.cruise.activity.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Nhật ký kiểm toán bất biến cho luồng đặt, hủy, hoàn tiền và chuyển xe tour bờ (KAN-374 / AC6)
 */
@Entity
@Table(name = "excursion_booking_logs", indexes = {
        @Index(name = "idx_exc_book_log_excursion", columnList = "excursion_id"),
        @Index(name = "idx_exc_book_log_passenger", columnList = "passenger_id"),
        @Index(name = "idx_exc_book_log_booking", columnList = "booking_id"),
        @Index(name = "idx_exc_book_log_action", columnList = "action")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionBookingLog extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "excursion_id", nullable = false)
    private Long excursionId;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "ticket_code", length = 100)
    private String ticketCode;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "charged_amount", precision = 12, scale = 2)
    private BigDecimal chargedAmount;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "penalty_fee", precision = 12, scale = 2)
    private BigDecimal penaltyFee;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "bus_number", length = 100)
    private String busNumber;

    @Column(name = "group_name", length = 100)
    private String groupName;

    @Column(name = "audit_time", nullable = false)
    private Instant auditTime;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "staff_id", length = 50)
    private String staffId;
}
