package com.cruise.activity.service;

import com.cruise.activity.dto.ExcursionOpsKpiResponse;
import com.cruise.activity.dto.ExcursionOpsKpiResponse.GuideRatingDto;
import com.cruise.activity.dto.ProviderScorecardDto;
import com.cruise.activity.entity.ExcursionGuide;
import com.cruise.activity.entity.ExcursionReview;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.GuideRole;
import com.cruise.activity.repository.ExcursionGuideRepository;
import com.cruise.activity.repository.ExcursionReviewRepository;
import com.cruise.activity.repository.ShoreExcursionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcursionOpsAnalyticsServiceTest {

    @Mock
    private ShoreExcursionRepository excursionRepository;

    @Mock
    private ExcursionReviewRepository reviewRepository;

    @Mock
    private ExcursionGuideRepository guideRepository;

    @InjectMocks
    private ExcursionOpsAnalyticsService service;

    private ShoreExcursion excursion1;
    private ShoreExcursion excursion2;
    private ExcursionReview review1;
    private ExcursionReview review2;
    private ExcursionGuide guide1;

    @BeforeEach
    void setUp() {
        excursion1 = new ShoreExcursion();
        excursion1.setId(1L);
        excursion1.setLocalProvider("Provider A");

        excursion2 = new ShoreExcursion();
        excursion2.setId(2L);
        excursion2.setLocalProvider(null);

        review1 = new ExcursionReview();
        review1.setId(1L);
        review1.setExcursionId(1L);
        review1.setRatingOverall(4);
        review1.setRatingGuide(5);
        review1.setRatingBus(4);
        review1.setRatingValue(3);
        review1.setIsFlagged(false);

        review2 = new ExcursionReview();
        review2.setId(2L);
        review2.setExcursionId(2L);
        review2.setRatingOverall(2);
        review2.setRatingGuide(2);
        review2.setRatingBus(2);
        review2.setRatingValue(2);
        review2.setIsFlagged(true);

        guide1 = new ExcursionGuide();
        guide1.setId(1L);
        guide1.setFullName("John Doe");
        guide1.setGuideCode("GD-001");
        guide1.setRole(GuideRole.LEAD);
        guide1.setRating(4.5);
    }

    @Test
    void getOpsKpiMetrics_shouldReturnCalculatedMetrics() {
        when(excursionRepository.findAll()).thenReturn(Arrays.asList(excursion1, excursion2));
        when(reviewRepository.findAll()).thenReturn(Arrays.asList(review1, review2));
        when(guideRepository.findAll()).thenReturn(Collections.singletonList(guide1));

        ExcursionOpsKpiResponse response = service.getOpsKpiMetrics();

        assertThat(response).isNotNull();
        assertThat(response.totalExcursions()).isEqualTo(2);
        assertThat(response.totalReviews()).isEqualTo(2);
        assertThat(response.totalFlaggedReviews()).isEqualTo(1);
        assertThat(response.averageOverallRating()).isEqualTo(3.0);
        assertThat(response.averageGuideRating()).isEqualTo(3.5);
        assertThat(response.averageBusRating()).isEqualTo(3.0);
        assertThat(response.averageValueRating()).isEqualTo(2.5);

        List<ProviderScorecardDto> scorecards = response.providerScorecards();
        assertThat(scorecards).hasSize(2);

        List<GuideRatingDto> guideRatings = response.guideRatings();
        assertThat(guideRatings).hasSize(1);
        assertThat(guideRatings.get(0).averageRating()).isEqualTo(4.5);
    }

    @Test
    void getProviderScorecards_shouldReturnScorecards() {
        when(excursionRepository.findAll()).thenReturn(Arrays.asList(excursion1, excursion2));
        when(reviewRepository.findAll()).thenReturn(Arrays.asList(review1, review2));

        List<ProviderScorecardDto> scorecards = service.getProviderScorecards();

        assertThat(scorecards).hasSize(2);
    }

    @Test
    void determinePerformanceTier_testBranches() {
        when(excursionRepository.findAll()).thenReturn(Collections.singletonList(excursion1));

        // High Risk
        ExcursionReview hrReview = new ExcursionReview();
        hrReview.setId(3L);
        hrReview.setExcursionId(1L);
        hrReview.setRatingOverall(2);
        when(reviewRepository.findAll()).thenReturn(Collections.singletonList(hrReview));

        List<ProviderScorecardDto> scorecards = service.getProviderScorecards();
        assertThat(scorecards.get(0).performanceTier()).isEqualTo("HIGH_RISK");

        // Needs Improvement Flagged > 2
        hrReview.setRatingOverall(4);
        hrReview.setIsFlagged(true);
        ExcursionReview hrReview2 = new ExcursionReview(); hrReview2.setExcursionId(1L); hrReview2.setRatingOverall(4); hrReview2.setIsFlagged(true);
        ExcursionReview hrReview3 = new ExcursionReview(); hrReview3.setExcursionId(1L); hrReview3.setRatingOverall(4); hrReview3.setIsFlagged(true);

        when(reviewRepository.findAll()).thenReturn(Arrays.asList(hrReview, hrReview2, hrReview3));
        scorecards = service.getProviderScorecards();
        assertThat(scorecards.get(0).performanceTier()).isEqualTo("NEEDS_IMPROVEMENT");

        // Excellent
        hrReview.setIsFlagged(false);
        hrReview.setRatingOverall(5);
        when(reviewRepository.findAll()).thenReturn(Collections.singletonList(hrReview));
        scorecards = service.getProviderScorecards();
        assertThat(scorecards.get(0).performanceTier()).isEqualTo("EXCELLENT");

        // Good
        hrReview.setRatingOverall(4);
        when(reviewRepository.findAll()).thenReturn(Collections.singletonList(hrReview));
        scorecards = service.getProviderScorecards();
        assertThat(scorecards.get(0).performanceTier()).isEqualTo("GOOD");

        // Unrated
        when(reviewRepository.findAll()).thenReturn(Collections.emptyList());
        scorecards = service.getProviderScorecards();
        assertThat(scorecards.get(0).performanceTier()).isEqualTo("UNRATED");

        // Needs Improvement
        ExcursionReview niReview = new ExcursionReview();
        niReview.setId(3L);
        niReview.setExcursionId(1L);
        niReview.setRatingOverall(3); // Average 3.0 (< 3.5), reviewCount > 0, flagged <= 2 -> Needs Improvement
        when(reviewRepository.findAll()).thenReturn(Collections.singletonList(niReview));
        scorecards = service.getProviderScorecards();
        assertThat(scorecards.get(0).performanceTier()).isEqualTo("NEEDS_IMPROVEMENT");
    }
}
