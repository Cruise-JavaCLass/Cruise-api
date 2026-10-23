package com.cruise.pos.repository;

import com.cruise.pos.dto.ReportExportJobResponse;
import com.cruise.pos.entity.ReportExportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportExportJobRepository extends JpaRepository<ReportExportJob, Long> {

    Optional<ReportExportJob> findByJobId(String jobId);

    @Query("SELECT new com.cruise.pos.dto.ReportExportJobResponse(" +
            "j.id, j.jobId, j.format, j.status, j.reportTitle, j.downloadUrl, j.fileSizeBytes, j.errorMessage, j.createdAt, j.completedAt) " +
            "FROM ReportExportJob j ORDER BY j.createdAt DESC, j.id DESC")
    Page<ReportExportJobResponse> findAllMetadata(Pageable pageable);

    Page<ReportExportJob> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
