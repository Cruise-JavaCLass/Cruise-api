package com.cruise.activity.entity;

import com.cruise.activity.enums.RfidCardStatus;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Thực thể quản lý kho thẻ trắng RFID / NFC và vòng đời định danh thẻ (RFID Card Pool Inventory)
 */
@Entity
@Table(name = "rfid_card_inventory", indexes = {
        @Index(name = "idx_rfid_card_uid", columnList = "card_uid", unique = true),
        @Index(name = "idx_rfid_card_status", columnList = "status"),
        @Index(name = "idx_rfid_card_batch", columnList = "batch_number"),
        @Index(name = "idx_rfid_assigned_passenger", columnList = "assigned_passenger_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RfidCardInventory extends BaseEntity {

    @Column(name = "card_uid", nullable = false, unique = true, length = 64)
    private String cardUid; // UID vật lý đọc từ chip NFC/RFID

    @Column(name = "card_type", nullable = false, length = 30)
    @Builder.Default
    private String cardType = "MIFARE_CLASSIC_1K"; // MIFARE_CLASSIC_1K, MIFARE_DESFIRE, NFC_NTAG213

    @Column(name = "batch_number", nullable = false, length = 50)
    private String batchNumber; // Mã lô nhập khẩu (e.g. BATCH-2026-Q1-001)

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private RfidCardStatus status = RfidCardStatus.AVAILABLE;

    @Column(name = "assigned_passenger_id")
    private Long assignedPassengerId;

    @Column(name = "assigned_passenger_name", length = 150)
    private String assignedPassengerName;

    @Column(name = "assigned_cabin_number", length = 50)
    private String assignedCabinNumber;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "blacklisted_at")
    private Instant blacklistedAt;

    @Column(name = "blacklist_reason", length = 255)
    private String blacklistReason;

    @Column(name = "blacklisted_by", length = 100)
    private String blacklistedBy;

    @Column(name = "notes", length = 500)
    private String notes;
}
