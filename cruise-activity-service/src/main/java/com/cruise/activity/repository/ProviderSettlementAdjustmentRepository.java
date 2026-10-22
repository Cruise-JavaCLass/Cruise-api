package com.cruise.activity.repository;

import com.cruise.activity.entity.ProviderSettlementAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository cho Provider Settlement Adjustment (KAN-332, KAN-334)
 */
@Repository
public interface ProviderSettlementAdjustmentRepository extends JpaRepository<ProviderSettlementAdjustment, Long> {
    List<ProviderSettlementAdjustment> findBySettlementId(Long settlementId);
}
