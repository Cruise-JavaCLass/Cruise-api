package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.CredentialStatus;
import com.cruise.pos.enums.CredentialType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "pos_credentials", indexes = {
        @Index(name = "idx_pos_cred_ident", columnList = "identifier", unique = true),
        @Index(name = "idx_pos_cred_passenger", columnList = "passenger_id"),
        @Index(name = "idx_pos_cred_cabin", columnList = "cabin_number"),
        @Index(name = "idx_pos_cred_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosCredential extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false, length = 32)
    private CredentialType credentialType;

    @Column(name = "identifier", nullable = false, unique = true, length = 128)
    private String identifier;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "cabin_number", nullable = false, length = 50)
    private String cabinNumber;

    @Column(name = "photo_url", length = 255)
    private String photoUrl;

    @Column(name = "is_minor", nullable = false)
    @Builder.Default
    private Boolean isMinor = false;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private CredentialStatus status = CredentialStatus.ACTIVE;

    @Column(name = "individual_spending_limit", precision = 12, scale = 2)
    private BigDecimal individualSpendingLimit;

    @Column(name = "spending_allowed", nullable = false)
    @Builder.Default
    private Boolean spendingAllowed = true;

    @Column(name = "alcohol_restricted", nullable = false)
    @Builder.Default
    private Boolean alcoholRestricted = false;
}
