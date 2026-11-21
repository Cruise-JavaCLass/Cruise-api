package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.CabinFolioStatus;
import com.cruise.pos.exception.BusinessRuleException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Folio chi tiêu của một cabin trong một hải trình (hạn mức tín dụng và số dư hiện tại).
 * Mỗi cặp (cabin, hải trình) chỉ có một folio.
 */
@Entity
@Table(name = "billing_cabin_folios",
        uniqueConstraints = @UniqueConstraint(name = "uk_billing_folio_cabin_voyage",
                columnNames = {"cabin_number", "voyage_id"}),
        indexes = @Index(name = "idx_billing_folio_cabin", columnList = "cabin_number"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CabinFolio extends BaseEntity {

    @Column(name = "cabin_number", nullable = false, length = 32)
    private String cabinNumber;

    @Column(name = "voyage_id", nullable = false)
    private Long voyageId;

    @Column(name = "credit_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "current_balance", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private CabinFolioStatus status = CabinFolioStatus.OPEN;

    /** Khóa lạc quan: hai giao dịch cùng sửa số dư thì giao dịch đến sau phải thử lại */
    @Version
    @Column(name = "version")
    private Long version;

    public boolean isOpen() {
        return status == CabinFolioStatus.OPEN;
    }

    /** Ghi nợ thêm vào folio */
    public void debit(BigDecimal amount) {
        this.currentBalance = this.currentBalance.add(amount);
    }

    /** Ghi giảm công nợ; số dư không bao giờ âm */
    public void credit(BigDecimal amount) {
        BigDecimal result = this.currentBalance.subtract(amount);
        this.currentBalance = result.signum() < 0 ? BigDecimal.ZERO : result;
    }

    /** Ràng buộc nghiệp vụ: hạn mức và số dư không được âm (kiểm tra trước khi ghi DB) */
    @PrePersist
    @PreUpdate
    void validateInvariants() {
        if (creditLimit == null || creditLimit.signum() < 0) {
            throw new BusinessRuleException("Hạn mức tín dụng của folio không được âm");
        }
        if (currentBalance == null || currentBalance.signum() < 0) {
            throw new BusinessRuleException("Số dư của folio không được âm");
        }
    }
}
