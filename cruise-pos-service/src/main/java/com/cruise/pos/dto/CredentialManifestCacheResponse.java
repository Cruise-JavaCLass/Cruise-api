package com.cruise.pos.dto;

import com.cruise.pos.enums.CredentialStatus;
import com.cruise.pos.enums.CredentialType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialManifestCacheResponse {

    private Instant generatedAt;
    private Integer totalActiveCount;
    private List<ManifestCredentialEntry> credentials;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManifestCredentialEntry {
        private String identifier;
        private CredentialType credentialType;
        private Long passengerId;
        private String fullName;
        private String cabinNumber;
        private String photoUrl;
        private CredentialStatus status;
        private Boolean spendingAllowed;
        private Boolean alcoholRestricted;
        private Boolean isMinor;
        private BigDecimal individualSpendingLimit;
    }
}
