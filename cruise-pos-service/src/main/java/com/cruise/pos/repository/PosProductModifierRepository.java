package com.cruise.pos.repository;

import com.cruise.pos.entity.PosProductModifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PosProductModifierRepository extends JpaRepository<PosProductModifier, Long> {
    List<PosProductModifier> findByProductIdOrderByDisplayOrderAsc(Long productId);
    void deleteByProductId(Long productId);
}
