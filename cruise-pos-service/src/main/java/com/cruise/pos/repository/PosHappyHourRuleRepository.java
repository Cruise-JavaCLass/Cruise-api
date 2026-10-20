package com.cruise.pos.repository;

import com.cruise.pos.entity.PosHappyHourRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PosHappyHourRuleRepository extends JpaRepository<PosHappyHourRule, Long> {

    /**
     * Returns all active Happy Hour rules.
     */
    List<PosHappyHourRule> findByIsActiveTrue();

    /**
     * Returns active outlet and shared Happy Hour rules; null selects all outlets.
     */
    @Query("SELECT r FROM PosHappyHourRule r WHERE (:outletId IS NULL OR r.outletId IS NULL OR r.outletId = :outletId) AND r.isActive = true")
    List<PosHappyHourRule> findActiveRulesByOutlet(@Param("outletId") Long outletId);
}
