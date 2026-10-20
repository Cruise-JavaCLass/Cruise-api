package com.cruise.pos.repository;

import com.cruise.pos.entity.PosProductModifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PosProductModifierRepository extends JpaRepository<PosProductModifier, Long> {
    /**
     * Returns modifiers for the product in ascending display order.
     */
    List<PosProductModifier> findByProductIdOrderByDisplayOrderAsc(Long productId);
    /**
     * Deletes all modifiers belonging to the specified product.
     */
    void deleteByProductId(Long productId);
}
