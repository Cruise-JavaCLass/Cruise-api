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
class ExcursionReviewItemWriterTest {

    @Mock
    private ExcursionReviewRepository reviewRepository;

    @InjectMocks
    private ExcursionReviewItemWriter reviewWriter;

    @Test
    void testWriteItem() {
        ExcursionReview review = new ExcursionReview();
        when(reviewRepository.save(review)).thenReturn(review);

        ExcursionReview result = reviewWriter.writeItem(review);

        assertThat(result).isNotNull();
        verify(reviewRepository).save(review);
    }
}
