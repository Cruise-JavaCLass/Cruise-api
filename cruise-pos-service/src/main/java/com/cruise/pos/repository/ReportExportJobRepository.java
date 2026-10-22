package com.cruise.pos.repository;

import com.cruise.pos.entity.ReportExportJob;
import com.cruise.pos.enums.ExportJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportExportJobRepository extends JpaRepository<ReportExportJob, Long> {

    Optional<ReportExportJob> findByJobId(String jobId);

    Page<ReportExportJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
