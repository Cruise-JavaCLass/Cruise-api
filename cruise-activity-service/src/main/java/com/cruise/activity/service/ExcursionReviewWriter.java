package com.cruise.activity.service;

import com.cruise.activity.entity.ExcursionReview;
import com.cruise.activity.repository.ExcursionReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helper writer that persists a single ExcursionReview in an isolated REQUIRES_NEW transaction.
 */
@Service
@RequiredArgsConstructor
public class ExcursionReviewWriter {

    private final ExcursionReviewRepository reviewRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExcursionReview writeReview(ExcursionReview review) {
        return reviewRepository.saveAndFlush(review);
    }
}
