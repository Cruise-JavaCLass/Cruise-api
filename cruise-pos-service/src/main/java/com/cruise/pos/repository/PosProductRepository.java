package com.cruise.pos.repository;

import com.cruise.pos.entity.PosProduct;
import com.cruise.pos.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PosProductRepository extends JpaRepository<PosProduct, Long> {

    Optional<PosProduct> findBySku(String sku);

    Optional<PosProduct> findByBarcode(String barcode);

    @Query("SELECT p FROM PosProduct p WHERE (p.barcode = :code OR p.sku = :code) AND (:outletId IS NULL OR p.outletId IS NULL OR p.outletId = :outletId)")
    List<PosProduct> findByCodeAndOutlet(@Param("code") String code, @Param("outletId") Long outletId);

    boolean existsBySku(String sku);

    boolean existsByBarcode(String barcode);

    @Query("SELECT p FROM PosProduct p WHERE " +
           "(:outletId IS NULL OR p.outletId IS NULL OR p.outletId = :outletId) AND " +
           "(:categoryId IS NULL OR p.categoryId = :categoryId) AND " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<PosProduct> searchProducts(
            @Param("outletId") Long outletId,
            @Param("categoryId") Long categoryId,
            @Param("status") ProductStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("SELECT p FROM PosProduct p WHERE " +
           "(:outletId IS NULL OR p.outletId IS NULL OR p.outletId = :outletId) AND " +
           "p.status <> 'ARCHIVED' ORDER BY p.displayOrder ASC, p.name ASC")
    List<PosProduct> findActiveCatalogByOutlet(@Param("outletId") Long outletId);
}
