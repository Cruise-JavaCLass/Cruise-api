package com.cruise.activity.service;

import com.cruise.activity.entity.ExcursionReview;
import com.cruise.activity.repository.ExcursionReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcursionReviewWriterTest {

    @Mock
    private ExcursionReviewRepository reviewRepository;

    @InjectMocks
    private ExcursionReviewWriter excursionReviewWriter;

    @Test
    void saveReview_ShouldCallSaveAndFlush() {
        ExcursionReview review = new ExcursionReview();
        review.setId(1L);

        when(reviewRepository.saveAndFlush(review)).thenReturn(review);

        ExcursionReview savedReview = excursionReviewWriter.saveReview(review);

        assertThat(savedReview).isEqualTo(review);
        verify(reviewRepository).saveAndFlush(review);
    }
}
