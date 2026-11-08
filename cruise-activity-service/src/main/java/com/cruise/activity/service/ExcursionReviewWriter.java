package com.cruise.activity.service;

import com.cruise.activity.entity.ExcursionReview;
import com.cruise.activity.repository.ExcursionReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helper writer that persists a single excursion review in an isolated REQUIRES_NEW transaction.
 * This ensures that any database constraint violation (duplicate transaction or idempotency key)
 * is isolated and does not mark the caller batch's transaction as rollback-only.
 */
@Service
@RequiredArgsConstructor
public class ExcursionReviewWriter {

    private final ExcursionReviewRepository reviewRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExcursionReview saveReview(ExcursionReview review) {
        return reviewRepository.saveAndFlush(review);
    }
}
