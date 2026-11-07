package com.cruise.activity.service;

import com.cruise.activity.entity.ExcursionReview;
import com.cruise.activity.repository.ExcursionReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExcursionReviewWriterTest {

    @Mock
    private ExcursionReviewRepository reviewRepository;

    @InjectMocks
    private ExcursionReviewWriter reviewWriter;

    private ExcursionReview review;

    @BeforeEach
    void setUp() {
        review = ExcursionReview.builder()
                .excursionId(1L)
                .passengerId(100L)
                .ratingOverall(5)
                .build();
    }

    @Test
    void writeReview_ShouldSaveAndFlush() {
        when(reviewRepository.saveAndFlush(review)).thenReturn(review);

        ExcursionReview result = reviewWriter.writeReview(review);

        assertThat(result).isNotNull();
        verify(reviewRepository, times(1)).saveAndFlush(review);
    }
}
