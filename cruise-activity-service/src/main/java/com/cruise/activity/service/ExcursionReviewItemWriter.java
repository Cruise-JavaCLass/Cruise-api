package com.cruise.activity.service;

import com.cruise.activity.entity.ExcursionReview;
import com.cruise.activity.repository.ExcursionReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExcursionReviewItemWriter {

    private final ExcursionReviewRepository reviewRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExcursionReview writeItem(ExcursionReview review) {
        return reviewRepository.save(review);
    }
}
