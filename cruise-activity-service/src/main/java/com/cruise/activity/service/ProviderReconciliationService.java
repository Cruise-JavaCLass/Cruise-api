package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.engine.SettlementCalculationEngine;
import com.cruise.activity.entity.ExcursionPassenger;
import com.cruise.activity.entity.ProviderSettlement;
import com.cruise.activity.entity.ProviderSettlementAdjustment;
import com.cruise.activity.entity.ShoreExcursion;
import com.cruise.activity.enums.AdjustmentType;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.activity.enums.SettlementStatus;
import com.cruise.activity.repository.ExcursionPassengerRepository;
import com.cruise.activity.repository.ProviderSettlementAdjustmentRepository;
import com.cruise.activity.repository.ProviderSettlementRepository;
import com.cruise.activity.repository.ShoreExcursionRepository;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Service xử lý đối soát tài chính và quyết toán công nợ nhà cung cấp tour (KAN-332, KAN-333, KAN-334, KAN-335)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderReconciliationService {

    private final ProviderSettlementRepository settlementRepository;
    private final ProviderSettlementAdjustmentRepository adjustmentRepository;
    private final ShoreExcursionRepository excursionRepository;
    private final ExcursionPassengerRepository excursionPassengerRepository;
    private final SettlementCalculationEngine calculationEngine;

    @Transactional
    public SettlementDetailDto initializeOrRecalculateSettlement(InitializeSettlementRequest request) {
        Objects.requireNonNull(request, "InitializeSettlementRequest must not be null");
        ShoreExcursion excursion = excursionRepository.findById(request.excursionId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tour trên bờ với id: " + request.excursionId()));

        String providerName = excursion.getLocalProvider() != null && !excursion.getLocalProvider().isBlank()
                ? excursion.getLocalProvider() : "Chưa chỉ định";
        String providerId = request.providerId() != null && !request.providerId().isBlank()
                ? request.providerId() : sanitizeProviderId(providerName);

        ProviderSettlement settlement = settlementRepository
                .findByExcursionIdAndProviderIdWithLock(excursion.getId(), providerId)
                .orElseGet(() -> ProviderSettlement.builder()
                        .providerId(providerId)
                        .providerName(providerName)
                        .excursionId(excursion.getId())
                        .excursionTitle(excursion.getTitle())
                        .voyageId(excursion.getTourId())
                        .portId(excursion.getPortId())
                        .portName(excursion.getPortName())
                        .currency(excursion.getCurrency() != null ? excursion.getCurrency() : "USD")
                        .status(SettlementStatus.DRAFT)
                        .build());

        assertNotLocked(settlement, "Không thể cập nhật quyết toán khi đã được phê duyệt hoặc thanh toán");

        if (request.agreedUnitRate() != null) {
            settlement.setAgreedUnitRate(request.agreedUnitRate());
        } else if (settlement.getAgreedUnitRate() == null || settlement.getAgreedUnitRate().compareTo(BigDecimal.ZERO) == 0) {
            settlement.setAgreedUnitRate(excursion.getPrice());
        }

        if (request.minGuaranteePax() != null) {
            settlement.setMinGuaranteePax(request.minGuaranteePax());
        }

        if (request.notes() != null) {
            settlement.setNotes(request.notes());
        }

        // Đếm pax thực tế
        List<ExcursionPassenger> passengers = excursionPassengerRepository.findByExcursionIdAndActiveTrue(excursion.getId());
        int registeredPax = passengers.size();
        int actualPax = (int) passengers.stream()
                .filter(p -> p.getStatus() == ExcursionPassengerStatus.CHECKED_IN
                        || p.getStatus() == ExcursionPassengerStatus.ON_TOUR
                        || p.getStatus() == ExcursionPassengerStatus.RETURNED_SAFE)
                .count();
        int noShowPax = Math.max(0, registeredPax - actualPax);

        settlement.setRegisteredPax(registeredPax);
        settlement.setActualPax(actualPax);
        settlement.setNoShowPax(noShowPax);

        // Tính toán và lưu quyết toán
        try {
            recalculateAndSave(settlement);
        } catch (DataIntegrityViolationException e) {
            log.warn("Data integrity conflict during settlement save for excursionId={}, providerId={}", excursion.getId(), providerId);
            throw new BusinessRuleException("Xung đột dữ liệu khi khởi tạo quyết toán: bản ghi đã tồn tại");
        }

        log.info("Initialized/Recalculated settlement: id={}, excursionId={}, providerId={}, totalAmount={}",
                settlement.getId(), settlement.getExcursionId(), settlement.getProviderId(), settlement.getTotalAmount());

        return toDto(settlement);
    }

    @Transactional
    public SettlementDetailDto addAdjustment(Long settlementId, CreateSettlementAdjustmentRequest request) {
        Objects.requireNonNull(request, "CreateSettlementAdjustmentRequest must not be null");
        ProviderSettlement settlement = settlementRepository.findByIdWithLock(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quyết toán với id: " + settlementId));

        assertNotLocked(settlement, "Không thể thêm điều chỉnh vào quyết toán đã khóa (APPROVED/PAID/RECONCILED)");

        BigDecimal signedAmount = calculateSignedAdjustmentAmount(request.adjustmentType(), request.amount());

        ProviderSettlementAdjustment adjustment = ProviderSettlementAdjustment.builder()
                .settlement(settlement)
                .adjustmentType(request.adjustmentType())
                .amount(signedAmount)
                .invoiceReference(request.invoiceReference())
                .explanation(request.explanation())
                .recordedBy(request.recordedBy())
                .build();

        settlement.addAdjustment(adjustment);
        recalculateAndSave(settlement);

        log.info("Added adjustment to settlement: settlementId={}, type={}, amount={}, newTotal={}",
                settlementId, request.adjustmentType(), signedAmount, settlement.getTotalAmount());

        return toDto(settlement);
    }

    @Transactional
    public SettlementDetailDto updateStatus(Long settlementId, UpdateSettlementStatusRequest request) {
        Objects.requireNonNull(request, "UpdateSettlementStatusRequest must not be null");
        ProviderSettlement settlement = settlementRepository.findByIdWithLock(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quyết toán với id: " + settlementId));

        SettlementStatus target = request.targetStatus();
        if (settlement.getStatus() == target) {
            return toDto(settlement);
        }
        validateStatusTransition(settlement.getStatus(), target);

        settlement.setStatus(target);
        if (target == SettlementStatus.APPROVED) {
            settlement.setApprovedBy(request.actor() != null ? request.actor() : "SHORE_EXCURSION_MANAGER");
            settlement.setApprovedAt(Instant.now());
        }
        if (request.note() != null) {
            String existingNotes = settlement.getNotes() != null ? settlement.getNotes() + "; " : "";
            settlement.setNotes(existingNotes + "[" + target + "]: " + request.note());
        }

        ProviderSettlement saved = settlementRepository.save(settlement);
        log.info("Updated settlement status: id={}, from={}, to={}", settlementId, settlement.getStatus(), target);

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public SettlementDetailDto getSettlementById(Long settlementId) {
        ProviderSettlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quyết toán với id: " + settlementId));
        return toDto(settlement);
    }

    @Transactional(readOnly = true)
    public Page<SettlementDetailDto> searchSettlements(
            String providerId,
            Long voyageId,
            SettlementStatus status,
            Pageable pageable
    ) {
        return settlementRepository.searchSettlements(providerId, voyageId, status, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ProviderSettlementSummaryResponse getSettlementSummary(String providerId, Long voyageId) {
        List<ProviderSettlement> settlements = (voyageId != null)
                ? settlementRepository.findByProviderIdAndVoyageId(providerId, voyageId)
                : settlementRepository.findByProviderId(providerId);

        String providerName = settlements.stream()
                .findFirst()
                .map(ProviderSettlement::getProviderName)
                .orElse(providerId);

        int totalExcursions = settlements.size();
        int totalRegisteredPax = settlements.stream().mapToInt(ProviderSettlement::getRegisteredPax).sum();
        int totalActualPax = settlements.stream().mapToInt(ProviderSettlement::getActualPax).sum();
        int totalNoShowPax = settlements.stream().mapToInt(ProviderSettlement::getNoShowPax).sum();
        int totalBillablePax = settlements.stream().mapToInt(ProviderSettlement::getBillablePax).sum();

        BigDecimal totalBase = settlements.stream()
                .map(ProviderSettlement::getBaseAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAdjusted = settlements.stream()
                .map(ProviderSettlement::getAdjustedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grandTotal = settlements.stream()
                .map(ProviderSettlement::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String currency = settlements.stream()
                .findFirst()
                .map(ProviderSettlement::getCurrency)
                .orElse("USD");

        List<SettlementDetailDto> dtoList = settlements.stream().map(this::toDto).toList();

        return new ProviderSettlementSummaryResponse(
                providerId,
                providerName,
                voyageId,
                totalExcursions,
                totalRegisteredPax,
                totalActualPax,
                totalNoShowPax,
                totalBillablePax,
                totalBase,
                totalAdjusted,
                grandTotal,
                currency,
                dtoList
        );
    }

    private void recalculateAndSave(ProviderSettlement settlement) {
        BigDecimal sumAdjustments = settlement.getAdjustments() != null
                ? settlement.getAdjustments().stream()
                .map(ProviderSettlementAdjustment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                : BigDecimal.ZERO;

        SettlementCalculationEngine.CalculationResult result = calculationEngine.calculate(
                settlement.getActualPax(),
                settlement.getMinGuaranteePax(),
                settlement.getAgreedUnitRate(),
                sumAdjustments
        );

        settlement.setBillablePax(result.billablePax());
        settlement.setBaseAmount(result.baseAmount());
        settlement.setAdjustedAmount(result.adjustedAmount());
        settlement.setTotalAmount(result.totalAmount());

        settlementRepository.save(settlement);
    }

    private BigDecimal calculateSignedAdjustmentAmount(AdjustmentType type, BigDecimal inputAmount) {
        BigDecimal absAmount = inputAmount != null ? inputAmount.abs() : BigDecimal.ZERO;
        return switch (type) {
            case EXTRA_SERVICE, OTHER_COMPENSATION -> absAmount;
            case PENALTY_WEATHER_DELAY, PENALTY_VEHICLE_DEFECT, PENALTY_GUIDE_NO_SHOW -> absAmount.negate();
        };
    }

    private void assertNotLocked(ProviderSettlement settlement, String message) {
        if (settlement.getStatus() == SettlementStatus.APPROVED
                || settlement.getStatus() == SettlementStatus.PAID
                || settlement.getStatus() == SettlementStatus.RECONCILED) {
            throw new BusinessRuleException(message);
        }
    }

    private void validateStatusTransition(SettlementStatus current, SettlementStatus target) {
        if (current == target) {
            return;
        }
        boolean valid = switch (current) {
            case DRAFT -> target == SettlementStatus.PENDING_VERIFICATION;
            case PENDING_VERIFICATION -> target == SettlementStatus.APPROVED || target == SettlementStatus.DRAFT;
            case APPROVED -> target == SettlementStatus.PAID;
            case PAID -> target == SettlementStatus.RECONCILED;
            case RECONCILED -> false;
        };

        if (!valid) {
            throw new BusinessRuleException(String.format("Không thể chuyển trạng thái quyết toán từ %s sang %s", current, target));
        }
    }

    private String sanitizeProviderId(String providerName) {
        return providerName.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-");
    }

    private SettlementDetailDto toDto(ProviderSettlement s) {
        List<SettlementAdjustmentDto> adjustments = s.getAdjustments() != null
                ? s.getAdjustments().stream()
                .map(a -> new SettlementAdjustmentDto(
                        a.getId(),
                        a.getAdjustmentType(),
                        a.getAmount(),
                        a.getInvoiceReference(),
                        a.getExplanation(),
                        a.getRecordedBy(),
                        a.getCreatedAt()
                ))
                .toList()
                : Collections.emptyList();

        return new SettlementDetailDto(
                s.getId(),
                s.getProviderId(),
                s.getProviderName(),
                s.getExcursionId(),
                s.getExcursionTitle(),
                s.getVoyageId(),
                s.getPortId(),
                s.getPortName(),
                s.getAgreedUnitRate(),
                s.getMinGuaranteePax(),
                s.getRegisteredPax(),
                s.getActualPax(),
                s.getNoShowPax(),
                s.getBillablePax(),
                s.getBaseAmount(),
                s.getAdjustedAmount(),
                s.getTotalAmount(),
                s.getCurrency(),
                s.getStatus(),
                s.getApprovedBy(),
                s.getApprovedAt(),
                s.getNotes(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                adjustments
        );
    }
}
