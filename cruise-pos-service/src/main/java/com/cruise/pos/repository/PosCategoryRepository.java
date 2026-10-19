package com.cruise.pos.repository;

import com.cruise.pos.entity.PosCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PosCategoryRepository extends JpaRepository<PosCategory, Long> {
    Optional<PosCategory> findByCode(String code);
    List<PosCategory> findByOutletIdOrderByDisplayOrderAsc(Long outletId);
    List<PosCategory> findByParentId(Long parentId);
    List<PosCategory> findByIsActiveTrueOrderByDisplayOrderAsc();

    @Query("SELECT c FROM PosCategory c WHERE (:outletId IS NULL OR c.outletId IS NULL OR c.outletId = :outletId) AND c.isActive = true ORDER BY c.displayOrder ASC")
    List<PosCategory> findCategoriesByOutlet(@Param("outletId") Long outletId);
}
