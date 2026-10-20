package com.cruise.pos.repository;

import com.cruise.pos.entity.FolioInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FolioInvoiceRepository extends JpaRepository<FolioInvoice, Long> {

    Optional<FolioInvoice> findBySettlementUuid(String settlementUuid);

    Optional<FolioInvoice> findByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumber(String invoiceNumber);
}
