package com.cruise.pos.repository;

import com.cruise.pos.entity.PosCatalogAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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

    /**
     * Returns product IDs reassigned away from a specific outlet since a given timestamp,
     * or formerly global products restricted to a different outlet.
     */
    @Query("SELECT a.productId FROM PosCatalogAuditLog a " +
           "WHERE a.action = 'OUTLET_REASSIGNED' AND a.createdAt >= :since " +
           "AND (a.oldValue = :outletIdStr OR (a.oldValue = 'GLOBAL' AND a.newValue <> :outletIdStr))")
    List<Long> findReassignedProductIdsForOutlet(@Param("outletIdStr") String outletIdStr, @Param("since") Instant since);
}
