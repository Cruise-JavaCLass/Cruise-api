package com.cruise.auth.entity;

import com.cruise.auth.enums.VerificationStatus;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity đại diện cho liên kết giữa tài khoản người dùng (userId) và hồ sơ hành khách (passengerId)
 * trong từng chuyến đi (bookingReference) theo đặc tả Jira KAN-719.
 *
 * Ràng buộc duy nhất:
 * - (user_id, booking_reference): 1 tài khoản userId chỉ gắn với tối đa 1 hồ sơ hành khách trong cùng chuyến đi.
 * - (passenger_id): Mỗi hồ sơ hành khách chỉ được gắn với 1 tài khoản duy nhất.
 */
@Entity
@Table(name = "user_passenger_bindings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_passenger_binding_user_booking", columnNames = {"user_id", "booking_reference"}),
                @UniqueConstraint(name = "uk_user_passenger_binding_passenger", columnNames = {"passenger_id"})
        },
        indexes = {
                @Index(name = "idx_upb_user_id", columnList = "user_id"),
                @Index(name = "idx_upb_passenger_id", columnList = "passenger_id"),
                @Index(name = "idx_upb_booking_ref", columnList = "booking_reference")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPassengerBinding extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "booking_reference", nullable = false, length = 100)
    private String bookingReference;

    @Column(name = "cabin_number", nullable = false, length = 20)
    private String cabinNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
