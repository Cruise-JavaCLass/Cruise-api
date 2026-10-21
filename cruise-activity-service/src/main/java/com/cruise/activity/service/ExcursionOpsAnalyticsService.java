package com.cruise.activity.service;

import com.cruise.activity.dto.ExcursionOpsKpiResponse;
import com.cruise.activity.dto.ExcursionOpsKpiResponse.GuideRatingDto;
import com.cruise.activity.dto.ProviderScorecardDto;
import com.cruise.activity.entity.ExcursionGuide;
import com.cruise.activity.entity.ExcursionReview;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.repository.ExcursionGuideRepository;
import com.cruise.activity.repository.ExcursionReviewRepository;
import com.cruise.activity.repository.ShoreExcursionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service tính toán Ops KPI & Bảng điểm hiệu suất nhà cung cấp (Scorecard) (KAN-343)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExcursionOpsAnalyticsService {

    private final ShoreExcursionRepository excursionRepository;
    private final ExcursionReviewRepository reviewRepository;
    private final ExcursionGuideRepository guideRepository;

    @Transactional(readOnly = true)
    public ExcursionOpsKpiResponse getOpsKpiMetrics() {
        List<ShoreExcursion> excursions = excursionRepository.findAll();
        List<ExcursionReview> reviews = reviewRepository.findAll();
        List<ExcursionGuide> guides = guideRepository.findAll();

        long totalExcursions = excursions.size();
        long totalReviews = reviews.size();
        long totalFlaggedReviews = reviews.stream().filter(r -> Boolean.TRUE.equals(r.getIsFlagged())).count();

        double avgOverall = reviews.stream()
                .filter(r -> r.getRatingOverall() != null)
                .mapToInt(ExcursionReview::getRatingOverall)
                .average()
                .orElse(0.0);

        double avgGuide = reviews.stream()
                .filter(r -> r.getRatingGuide() != null)
                .mapToInt(ExcursionReview::getRatingGuide)
                .average()
                .orElse(0.0);

        double avgBus = reviews.stream()
                .filter(r -> r.getRatingBus() != null)
                .mapToInt(ExcursionReview::getRatingBus)
                .average()
                .orElse(0.0);

        double avgValue = reviews.stream()
                .filter(r -> r.getRatingValue() != null)
                .mapToInt(ExcursionReview::getRatingValue)
                .average()
                .orElse(0.0);

        List<ProviderScorecardDto> providerScorecards = buildProviderScorecards(excursions, reviews);
        List<GuideRatingDto> guideRatings = buildGuideRatings(guides);

        return new ExcursionOpsKpiResponse(
                totalExcursions,
                totalReviews,
                roundDouble(avgOverall),
                roundDouble(avgGuide),
                roundDouble(avgBus),
                roundDouble(avgValue),
                totalFlaggedReviews,
                providerScorecards,
                guideRatings
        );
    }

    @Transactional(readOnly = true)
    public List<ProviderScorecardDto> getProviderScorecards() {
        List<ShoreExcursion> excursions = excursionRepository.findAll();
        List<ExcursionReview> reviews = reviewRepository.findAll();
        return buildProviderScorecards(excursions, reviews);
    }

    private List<ProviderScorecardDto> buildProviderScorecards(List<ShoreExcursion> excursions, List<ExcursionReview> reviews) {
        Map<Long, String> excursionToProviderMap = excursions.stream()
                .collect(Collectors.toMap(
                        ShoreExcursion::getId,
                        e -> e.getLocalProvider() != null ? e.getLocalProvider() : "Chưa chỉ định",
                        (p1, p2) -> p1
                ));

        Map<String, List<ShoreExcursion>> excursionsByProvider = excursions.stream()
                .collect(Collectors.groupingBy(e -> e.getLocalProvider() != null ? e.getLocalProvider() : "Chưa chỉ định"));

        Map<String, List<ExcursionReview>> reviewsByProvider = reviews.stream()
                .collect(Collectors.groupingBy(r -> excursionToProviderMap.getOrDefault(r.getExcursionId(), "Chưa chỉ định")));

        return excursionsByProvider.entrySet().stream()
                .map(entry -> {
                    String provider = entry.getKey();
                    long excCount = entry.getValue().size();
                    List<ExcursionReview> pReviews = reviewsByProvider.getOrDefault(provider, Collections.emptyList());

                    long revCount = pReviews.size();
                    long flaggedCount = pReviews.stream().filter(r -> Boolean.TRUE.equals(r.getIsFlagged())).count();

                    double avgRating = pReviews.stream()
                            .filter(r -> r.getRatingOverall() != null)
                            .mapToInt(ExcursionReview::getRatingOverall)
                            .average()
                            .orElse(0.0);

                    double avgGuideRating = pReviews.stream()
                            .filter(r -> r.getRatingGuide() != null)
                            .mapToInt(ExcursionReview::getRatingGuide)
                            .average()
                            .orElse(0.0);

                    double avgBusRating = pReviews.stream()
                            .filter(r -> r.getRatingBus() != null)
                            .mapToInt(ExcursionReview::getRatingBus)
                            .average()
                            .orElse(0.0);

                    double avgValueRating = pReviews.stream()
                            .filter(r -> r.getRatingValue() != null)
                            .mapToInt(ExcursionReview::getRatingValue)
                            .average()
                            .orElse(0.0);

                    String tier = determinePerformanceTier(avgRating, revCount, flaggedCount);

                    return new ProviderScorecardDto(
                            provider,
                            excCount,
                            revCount,
                            roundDouble(avgRating),
                            roundDouble(avgGuideRating),
                            roundDouble(avgBusRating),
                            roundDouble(avgValueRating),
                            flaggedCount,
                            tier
                    );
                })
                .sorted(Comparator.comparing(ProviderScorecardDto::averageRating).reversed())
                .toList();
    }

    private List<GuideRatingDto> buildGuideRatings(List<ExcursionGuide> guides) {
        Map<String, List<ExcursionGuide>> guidesByName = guides.stream()
                .collect(Collectors.groupingBy(ExcursionGuide::getFullName));

        return guidesByName.entrySet().stream()
                .map(entry -> {
                    String name = entry.getKey();
                    List<ExcursionGuide> list = entry.getValue();
                    ExcursionGuide first = list.get(0);
                    double avgRating = list.stream()
                            .filter(g -> g.getRating() != null)
                            .mapToDouble(ExcursionGuide::getRating)
                            .average()
                            .orElse(5.0);

                    return new GuideRatingDto(
                            first.getId(),
                            name,
                            first.getGuideCode(),
                            first.getRole() != null ? first.getRole().name() : "LEAD",
                            roundDouble(avgRating),
                            (long) list.size()
                    );
                })
                .sorted(Comparator.comparing(GuideRatingDto::averageRating).reversed())
                .toList();
    }

    private String determinePerformanceTier(double avgRating, long reviewCount, long flaggedCount) {
        if (reviewCount > 0 && avgRating < 3.0) {
            return "HIGH_RISK";
        }
        if (flaggedCount > 2) {
            return "NEEDS_IMPROVEMENT";
        }
        if (avgRating >= 4.5) {
            return "EXCELLENT";
        }
        if (avgRating >= 3.5) {
            return "GOOD";
        }
        if (reviewCount == 0) {
            return "UNRATED";
        }
        return "NEEDS_IMPROVEMENT";
    }

    private double roundDouble(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
