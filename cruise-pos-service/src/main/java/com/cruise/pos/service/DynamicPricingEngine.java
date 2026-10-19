package com.cruise.pos.service;

import com.cruise.pos.dto.HappyHourRuleDto;
import com.cruise.pos.entity.PosHappyHourRule;
import com.cruise.pos.entity.PosProduct;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.PosHappyHourRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicPricingEngine {

    private final PosHappyHourRuleRepository happyHourRuleRepository;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Transactional(readOnly = true)
    public List<HappyHourRuleDto> getRulesByOutlet(Long outletId) {
        return happyHourRuleRepository.findActiveRulesByOutlet(outletId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<PosHappyHourRule> findActiveRule(Long outletId, LocalTime checkTime, DayOfWeek dayOfWeek) {
        List<PosHappyHourRule> rules = happyHourRuleRepository.findActiveRulesByOutlet(outletId);
        for (PosHappyHourRule rule : rules) {
            if (isRuleActive(rule, checkTime, dayOfWeek)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    public boolean isRuleActive(PosHappyHourRule rule, LocalTime checkTime, DayOfWeek dayOfWeek) {
        if (!Boolean.TRUE.equals(rule.getIsActive())) {
            return false;
        }

        if (rule.getDaysOfWeek() != null && !rule.getDaysOfWeek().equalsIgnoreCase("ALL")) {
            if (!rule.getDaysOfWeek().toUpperCase().contains(dayOfWeek.name())) {
                return false;
            }
        }

        try {
            LocalTime start = LocalTime.parse(rule.getStartTime(), TIME_FORMATTER);
            LocalTime end = LocalTime.parse(rule.getEndTime(), TIME_FORMATTER);

            if (start.isBefore(end)) {
                return !checkTime.isBefore(start) && !checkTime.isAfter(end);
            } else {
                // Overnight rule (e.g. 22:00 to 02:00)
                return !checkTime.isBefore(start) || !checkTime.isAfter(end);
            }
        } catch (Exception e) {
            log.warn("Invalid time format in happy hour rule ID={}: {}", rule.getId(), e.getMessage());
            return false;
        }
    }

    public BigDecimal calculateItemDiscount(PosProduct product, BigDecimal grossPrice, PosHappyHourRule rule) {
        if (!Boolean.TRUE.equals(product.getHappyHourEligible())) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        }

        BigDecimal discountPercent = BigDecimal.ZERO;
        if (rule != null && rule.getDiscountPercent() != null) {
            discountPercent = rule.getDiscountPercent();
        } else if (product.getHappyHourDiscountPercent() != null && product.getHappyHourDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
            discountPercent = product.getHappyHourDiscountPercent();
        }

        if (discountPercent.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        }

        BigDecimal discount = grossPrice.multiply(discountPercent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN);

        return discount;
    }

    @Transactional
    public HappyHourRuleDto createRule(HappyHourRuleDto dto) {
        PosHappyHourRule entity = PosHappyHourRule.builder()
                .outletId(dto.getOutletId())
                .name(dto.getName())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .discountPercent(dto.getDiscountPercent())
                .daysOfWeek(dto.getDaysOfWeek() != null ? dto.getDaysOfWeek() : "ALL")
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .description(dto.getDescription())
                .build();
        return toDto(happyHourRuleRepository.save(entity));
    }

    @Transactional
    public HappyHourRuleDto updateRule(Long id, HappyHourRuleDto dto) {
        PosHappyHourRule entity = happyHourRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quy tắc Happy Hour với ID: " + id));

        if (dto.getName() != null) entity.setName(dto.getName());
        if (dto.getStartTime() != null) entity.setStartTime(dto.getStartTime());
        if (dto.getEndTime() != null) entity.setEndTime(dto.getEndTime());
        if (dto.getDiscountPercent() != null) entity.setDiscountPercent(dto.getDiscountPercent());
        if (dto.getDaysOfWeek() != null) entity.setDaysOfWeek(dto.getDaysOfWeek());
        if (dto.getIsActive() != null) entity.setIsActive(dto.getIsActive());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());

        return toDto(happyHourRuleRepository.save(entity));
    }

    @Transactional
    public void deleteRule(Long id) {
        if (!happyHourRuleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy quy tắc Happy Hour với ID: " + id);
        }
        happyHourRuleRepository.deleteById(id);
    }

    private HappyHourRuleDto toDto(PosHappyHourRule r) {
        return HappyHourRuleDto.builder()
                .id(r.getId())
                .outletId(r.getOutletId())
                .name(r.getName())
                .startTime(r.getStartTime())
                .endTime(r.getEndTime())
                .discountPercent(r.getDiscountPercent())
                .daysOfWeek(r.getDaysOfWeek())
                .isActive(r.getIsActive())
                .description(r.getDescription())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
