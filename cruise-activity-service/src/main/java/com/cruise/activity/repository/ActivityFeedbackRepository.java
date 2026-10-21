package com.cruise.activity.repository;

import com.cruise.activity.entity.ActivityFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityFeedbackRepository extends JpaRepository<ActivityFeedback, Long> {

    Optional<ActivityFeedback> findByActivityIdAndPassengerId(Long activityId, Long passengerId);

    Optional<ActivityFeedback> findByIdempotencyKey(String idempotencyKey);

    List<ActivityFeedback> findByActivityIdOrderByCreatedAtDesc(Long activityId);

    Page<ActivityFeedback> findByActivityId(Long activityId, Pageable pageable);

    List<ActivityFeedback> findByActivityIdAndEscalatedTrueOrderByCreatedAtDesc(Long activityId);

    long countByActivityId(Long activityId);

    long countByActivityIdAndEscalatedTrue(Long activityId);
}
