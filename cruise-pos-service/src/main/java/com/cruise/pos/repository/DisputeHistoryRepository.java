package com.cruise.pos.repository;

import com.cruise.pos.entity.DisputeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisputeHistoryRepository extends JpaRepository<DisputeHistory, Long> {
    List<DisputeHistory> findByDisputeIdOrderByTimestampDesc(Long disputeId);
}
