package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.PosTerminalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "pos_terminals", indexes = {
        @Index(name = "idx_terminal_device_id", columnList = "device_id", unique = true),
        @Index(name = "idx_terminal_station_id", columnList = "station_id"),
        @Index(name = "idx_terminal_status", columnList = "status"),
        @Index(name = "idx_terminal_api_key_hash", columnList = "api_key_hash")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosTerminal extends BaseEntity {

    @Column(name = "device_id", nullable = false, unique = true, length = 64)
    private String deviceId;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Column(name = "station_id", nullable = false, length = 64)
    private String stationId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "mac_address", length = 32)
    private String macAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private PosTerminalStatus status = PosTerminalStatus.PROVISIONED;

    @Column(name = "api_key_hash", length = 128)
    private String apiKeyHash;

    @Column(name = "api_key_prefix", length = 16)
    private String apiKeyPrefix;

    @Column(name = "certificate_fingerprint", length = 128)
    private String certificateFingerprint;

    @Column(name = "certificate_expiry")
    private Instant certificateExpiry;

    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private Integer tokenVersion = 1;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "firmware_version", length = 32)
    private String firmwareVersion;

    @Column(name = "remote_wipe_command", length = 64)
    private String remoteWipeCommand;

    @Column(name = "remote_wipe_requested_at")
    private Instant remoteWipeRequestedAt;

    @Column(name = "remote_wipe_acknowledged_at")
    private Instant remoteWipeAcknowledgedAt;
}
