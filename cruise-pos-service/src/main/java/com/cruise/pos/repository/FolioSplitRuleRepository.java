package com.cruise.pos.repository;

import com.cruise.pos.entity.FolioSplitRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FolioSplitRuleRepository extends JpaRepository<FolioSplitRule, Long> {

    List<FolioSplitRule> findByFolio_Id(Long folioId);
}
