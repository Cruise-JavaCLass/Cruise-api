package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pos_cabin_members", indexes = {
        @Index(name = "idx_member_cabin", columnList = "cabin_number"),
        @Index(name = "idx_member_passenger", columnList = "passenger_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CabinMember extends BaseEntity {

    @Column(name = "cabin_number", nullable = false, length = 32)
    private String cabinNumber;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "guest_name", nullable = false, length = 128)
    private String guestName;

    @Column(name = "age")
    private Integer age;

    @Column(name = "is_minor", nullable = false)
    @Builder.Default
    private Boolean isMinor = false;

    @Column(name = "is_authorized_signer", nullable = false)
    @Builder.Default
    private Boolean isAuthorizedSigner = true;

    @Column(name = "individual_credit_limit", precision = 12, scale = 2)
    private BigDecimal individualCreditLimit;

    @Column(name = "individual_balance", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal individualBalance = BigDecimal.ZERO;
}
