package com.cruise.activity.repository;

import com.cruise.activity.entity.ExcursionReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExcursionReviewRepository extends JpaRepository<ExcursionReview, Long> {

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    <S extends ExcursionReview> S save(S entity);

    Optional<ExcursionReview> findByExcursionIdAndPassengerId(Long excursionId, Long passengerId);

    boolean existsByExcursionIdAndPassengerId(Long excursionId, Long passengerId);

    Optional<ExcursionReview> findByIdempotencyKey(String idempotencyKey);

    Page<ExcursionReview> findByExcursionId(Long excursionId, Pageable pageable);

    List<ExcursionReview> findByExcursionId(Long excursionId);

    Page<ExcursionReview> findByIsFlaggedTrue(Pageable pageable);

    List<ExcursionReview> findByIsFlaggedTrue();

    @Query("SELECT AVG(r.ratingOverall) FROM ExcursionReview r WHERE r.excursionId = :excursionId")
    Double getAverageOverallRatingByExcursionId(@Param("excursionId") Long excursionId);

    @Query("SELECT AVG(r.ratingGuide) FROM ExcursionReview r WHERE r.excursionId = :excursionId")
    Double getAverageGuideRatingByExcursionId(@Param("excursionId") Long excursionId);

    @Query("SELECT AVG(r.ratingBus) FROM ExcursionReview r WHERE r.excursionId = :excursionId")
    Double getAverageBusRatingByExcursionId(@Param("excursionId") Long excursionId);

    @Query("SELECT AVG(r.ratingValue) FROM ExcursionReview r WHERE r.excursionId = :excursionId")
    Double getAverageValueRatingByExcursionId(@Param("excursionId") Long excursionId);

    long countByExcursionId(Long excursionId);

    long countByExcursionIdAndIsFlaggedTrue(Long excursionId);
}
