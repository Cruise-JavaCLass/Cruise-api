package com.cruise.activity.repository;

import com.cruise.activity.entity.PassengerServiceReview;
import com.cruise.activity.enums.FeedbackCategory;
import com.cruise.activity.enums.FeedbackStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PassengerServiceReviewRepository extends JpaRepository<PassengerServiceReview, Long> {

    List<PassengerServiceReview> findByPassengerId(Long passengerId);

    Page<PassengerServiceReview> findByStatus(FeedbackStatus status, Pageable pageable);

    @Query("SELECT r FROM PassengerServiceReview r WHERE " +
            "(:status IS NULL OR r.status = :status) AND " +
            "(:category IS NULL OR r.category = :category) AND " +
            "(:isAlert IS NULL OR r.isLowRatingAlert = :isAlert) " +
            "ORDER BY r.createdAt DESC")
    Page<PassengerServiceReview> searchReviews(
            @Param("status") FeedbackStatus status,
            @Param("category") FeedbackCategory category,
            @Param("isAlert") Boolean isAlert,
            Pageable pageable
    );

    @Query("SELECT COUNT(r) FROM PassengerServiceReview r WHERE r.passengerId = :passengerId AND r.createdAt > :since")
    long countRecentSubmissionsByPassenger(@Param("passengerId") Long passengerId, @Param("since") Instant since);
}
