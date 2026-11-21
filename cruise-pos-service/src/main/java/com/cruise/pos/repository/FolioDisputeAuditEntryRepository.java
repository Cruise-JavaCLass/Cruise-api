package com.cruise.pos.repository;

import com.cruise.pos.entity.FolioDisputeAuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FolioDisputeAuditEntryRepository extends JpaRepository<FolioDisputeAuditEntry, Long> {

    List<FolioDisputeAuditEntry> findByDispute_IdOrderByIdAsc(Long disputeId);
}
