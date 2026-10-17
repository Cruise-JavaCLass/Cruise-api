package com.cruise.activity.service;

import com.cruise.activity.dto.PassengerCatalogItemResponse;
import com.cruise.activity.entity.OnboardActivity;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.CatalogItemType;
import com.cruise.activity.repository.OnboardActivityRepository;
import com.cruise.activity.repository.ShoreExcursionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service xử lý tìm kiếm và duyệt danh mục cho hành khách (Passenger Catalog Browsing - KAN-211)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PassengerCatalogService {

    private final OnboardActivityRepository activityRepository;
    private final ShoreExcursionRepository excursionRepository;

    public Page<PassengerCatalogItemResponse> searchCatalog(
            Long tourId,
            Integer dayNumber,
            Long portId,
            CatalogItemType itemType,
            Boolean isIncluded,
            String keyword,
            Pageable pageable
    ) {
        List<PassengerCatalogItemResponse> allItems = new ArrayList<>();

        if (itemType == null || itemType == CatalogItemType.ONBOARD_ACTIVITY) {
            List<OnboardActivity> activities = activityRepository.findByTourIdAndActiveTrueOrderByActivityDateAscStartTimeAsc(tourId);
            for (OnboardActivity act : activities) {
                if (matchesActivityFilters(act, dayNumber, isIncluded, keyword)) {
                    allItems.add(mapFromActivity(act));
                }
            }
        }

        if (itemType == null || itemType == CatalogItemType.SHORE_EXCURSION) {
            List<ShoreExcursion> excursions = excursionRepository.findByTourIdAndActiveTrueOrderByExcursionDateAscGatheringTimeAsc(tourId);
            for (ShoreExcursion exc : excursions) {
                if (matchesExcursionFilters(exc, dayNumber, portId, isIncluded, keyword)) {
                    allItems.add(mapFromExcursion(exc));
                }
            }
        }

        // Sort by activityDate, then by startTime / gatheringTime
        allItems.sort(Comparator.comparing(PassengerCatalogItemResponse::getActivityDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(item -> item.getStartTime() != null ? item.getStartTime() : item.getGatheringTime(), Comparator.nullsLast(Comparator.naturalOrder())));

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allItems.size());
        List<PassengerCatalogItemResponse> pageContent = (start <= allItems.size()) ? allItems.subList(start, end) : List.of();

        return new PageImpl<>(pageContent, pageable, allItems.size());
    }

    private boolean matchesActivityFilters(OnboardActivity act, Integer dayNumber, Boolean isIncluded, String keyword) {
        if (dayNumber != null && !dayNumber.equals(act.getDayNumber())) {
            return false;
        }
        if (isIncluded != null && !isIncluded.equals(act.getIsIncluded())) {
            return false;
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase();
            boolean matchTitle = act.getTitle() != null && act.getTitle().toLowerCase().contains(kw);
            boolean matchDesc = act.getDescription() != null && act.getDescription().toLowerCase().contains(kw);
            boolean matchLoc = act.getLocation() != null && act.getLocation().toLowerCase().contains(kw);
            boolean matchCat = act.getCategory() != null && act.getCategory().toLowerCase().contains(kw);
            if (!matchTitle && !matchDesc && !matchLoc && !matchCat) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesExcursionFilters(ShoreExcursion exc, Integer dayNumber, Long portId, Boolean isIncluded, String keyword) {
        if (dayNumber != null && !dayNumber.equals(exc.getDayNumber())) {
            return false;
        }
        if (portId != null && !portId.equals(exc.getPortId())) {
            return false;
        }
        if (isIncluded != null && !isIncluded.equals(exc.getIsIncluded())) {
            return false;
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase();
            boolean matchTitle = exc.getTitle() != null && exc.getTitle().toLowerCase().contains(kw);
            boolean matchDesc = exc.getDescription() != null && exc.getDescription().toLowerCase().contains(kw);
            boolean matchPort = exc.getPortName() != null && exc.getPortName().toLowerCase().contains(kw);
            boolean matchMeeting = exc.getMeetingPoint() != null && exc.getMeetingPoint().toLowerCase().contains(kw);
            if (!matchTitle && !matchDesc && !matchPort && !matchMeeting) {
                return false;
            }
        }
        return true;
    }

    private PassengerCatalogItemResponse mapFromActivity(OnboardActivity act) {
        int maxCap = act.getMaxCapacity() != null ? act.getMaxCapacity() : 0;
        int curr = act.getCurrentParticipants() != null ? act.getCurrentParticipants() : 0;
        int remaining = Math.max(0, maxCap - curr);
        boolean isSoldOut = maxCap > 0 && remaining == 0;

        return PassengerCatalogItemResponse.builder()
                .id(act.getId())
                .tourId(act.getTourId())
                .itemType(CatalogItemType.ONBOARD_ACTIVITY)
                .title(act.getTitle())
                .description(act.getDescription())
                .category(act.getCategory())
                .locationOrPort(act.getLocation())
                .portId(null)
                .dayNumber(act.getDayNumber())
                .activityDate(act.getActivityDate())
                .startTime(act.getStartTime())
                .endTime(act.getEndTime())
                .gatheringTime(act.getStartTime())
                .departureTime(act.getStartTime())
                .returnToShipTime(act.getEndTime())
                .maxCapacity(act.getMaxCapacity())
                .currentParticipants(curr)
                .remainingCapacity(remaining)
                .isIncluded(act.getIsIncluded() != null ? act.getIsIncluded() : true)
                .price(act.getPrice() != null ? act.getPrice() : BigDecimal.ZERO)
                .childPrice(BigDecimal.ZERO)
                .currency("USD")
                .status(act.getStatus() != null ? act.getStatus().name() : "SCHEDULED")
                .isSoldOut(isSoldOut)
                .build();
    }

    private PassengerCatalogItemResponse mapFromExcursion(ShoreExcursion exc) {
        int maxCap = exc.getMaxCapacity() != null ? exc.getMaxCapacity() : 0;
        int curr = exc.getCurrentParticipants() != null ? exc.getCurrentParticipants() : 0;
        int remaining = Math.max(0, maxCap - curr);
        boolean isSoldOut = maxCap > 0 && remaining == 0;

        return PassengerCatalogItemResponse.builder()
                .id(exc.getId())
                .tourId(exc.getTourId())
                .itemType(CatalogItemType.SHORE_EXCURSION)
                .title(exc.getTitle())
                .description(exc.getDescription())
                .category("SHORE_EXCURSION")
                .locationOrPort(exc.getPortName())
                .portId(exc.getPortId())
                .dayNumber(exc.getDayNumber())
                .activityDate(exc.getExcursionDate())
                .startTime(exc.getGatheringTime())
                .endTime(exc.getReturnToShipTime())
                .gatheringTime(exc.getGatheringTime())
                .departureTime(exc.getDepartureTime())
                .returnToShipTime(exc.getReturnToShipTime())
                .maxCapacity(exc.getMaxCapacity())
                .currentParticipants(curr)
                .remainingCapacity(remaining)
                .isIncluded(exc.getIsIncluded() != null ? exc.getIsIncluded() : false)
                .price(exc.getPrice() != null ? exc.getPrice() : BigDecimal.ZERO)
                .childPrice(exc.getChildPrice() != null ? exc.getChildPrice() : BigDecimal.ZERO)
                .currency(exc.getCurrency() != null ? exc.getCurrency() : "USD")
                .status(exc.getStatus() != null ? exc.getStatus().name() : "SCHEDULED")
                .isSoldOut(isSoldOut)
                .build();
    }
}
