package com.cruise.pos.repository;

import com.cruise.pos.entity.PosCatalogAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PosCatalogAuditLogRepository extends JpaRepository<PosCatalogAuditLog, Long> {
    /**
     * Returns audit entries for a SKU ordered from newest to oldest.
     */
    List<PosCatalogAuditLog> findBySkuOrderByCreatedAtDesc(String sku);
    /**
     * Returns a page of audit entries ordered from newest to oldest.
     */
    Page<PosCatalogAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
