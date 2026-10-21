package com.cruise.pos.service;

import com.cruise.pos.dto.CreateFolioDisputeRequest;
import com.cruise.pos.dto.DisputeResponse;
import com.cruise.pos.dto.FolioTransactionDto;
import com.cruise.pos.dto.PassengerFolioSummaryResponse;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.entity.DisputeCase;
import com.cruise.pos.entity.DisputeHistory;
import com.cruise.pos.entity.PosTransaction;
import com.cruise.pos.entity.PosTransactionItem;
import com.cruise.pos.enums.DisputeStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.DisputeCaseRepository;
import com.cruise.pos.repository.DisputeHistoryRepository;
import com.cruise.pos.repository.PosTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PassengerFolioService {

    public static final String CAT_FOOD_AND_BEVERAGE = "FOOD_AND_BEVERAGE";
    public static final String CAT_SHORE_EXCURSIONS = "SHORE_EXCURSIONS";
    public static final String CAT_SPA_AND_WELLNESS = "SPA_AND_WELLNESS";
    public static final String CAT_RETAIL_SHOPPING = "RETAIL_SHOPPING";
    public static final String CAT_SERVICE_FEES = "SERVICE_FEES";

    private static final List<String> STANDARD_CATEGORIES = List.of(
            CAT_FOOD_AND_BEVERAGE,
            CAT_SHORE_EXCURSIONS,
            CAT_SPA_AND_WELLNESS,
            CAT_RETAIL_SHOPPING,
            CAT_SERVICE_FEES
    );

    private final CabinFolioAccountRepository cabinFolioAccountRepository;
    private final PosTransactionRepository posTransactionRepository;
    private final DisputeCaseRepository disputeCaseRepository;
    private final DisputeHistoryRepository disputeHistoryRepository;

    @Transactional(readOnly = true)
    public PassengerFolioSummaryResponse getFolioSummary(Long passengerId) {
        CabinFolioAccount account = findAccountByPassengerId(passengerId);
        List<PosTransaction> transactions = getTransactionsForAccount(account);

        Map<String, BigDecimal> breakdown = initStandardBreakdown();
        for (PosTransaction tx : transactions) {
            if (tx.getItems() != null && !tx.getItems().isEmpty()) {
                for (PosTransactionItem item : tx.getItems()) {
                    String cat = mapToStandardCategory(item.getCategory());
                    BigDecimal itemTotal = item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO;
                    breakdown.put(cat, breakdown.get(cat).add(itemTotal));
                }
            } else if (tx.getTotalAmount() != null) {
                String cat = mapToStandardCategory(tx.getNotes());
                breakdown.put(cat, breakdown.get(cat).add(tx.getTotalAmount()));
            }
        }

        BigDecimal creditLimit = account.getCreditLimit() != null ? account.getCreditLimit() : BigDecimal.ZERO;
        BigDecimal currentBalance = account.getCurrentBalance() != null ? account.getCurrentBalance() : BigDecimal.ZERO;
        BigDecimal availableLimit = account.getAvailableLimit() != null ? account.getAvailableLimit() : creditLimit.subtract(currentBalance);

        BigDecimal spendingRatio = BigDecimal.ZERO;
        BigDecimal spendingPercentage = BigDecimal.ZERO;
        if (creditLimit.compareTo(BigDecimal.ZERO) > 0) {
            spendingRatio = currentBalance.divide(creditLimit, 4, RoundingMode.HALF_UP);
            spendingPercentage = spendingRatio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        }

        boolean warningThresholdReached = spendingRatio.compareTo(new BigDecimal("0.80")) >= 0;
        boolean limitExceeded = spendingRatio.compareTo(new BigDecimal("1.00")) >= 0;

        return PassengerFolioSummaryResponse.builder()
                .passengerId(account.getPassengerId())
                .cabinNumber(account.getCabinNumber())
                .passengerName(account.getPrimaryGuestName())
                .currentBalance(currentBalance)
                .creditLimit(creditLimit)
                .availableLimit(availableLimit)
                .spendingPercentage(spendingPercentage)
                .warningThresholdReached(warningThresholdReached)
                .limitExceeded(limitExceeded)
                .status(account.getStatus() != null ? account.getStatus().name() : "ACTIVE")
                .spendingByCategory(breakdown)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<FolioTransactionDto> getTransactions(Long passengerId, String categoryFilter, Pageable pageable) {
        CabinFolioAccount account = findAccountByPassengerId(passengerId);
        List<PosTransaction> allTransactions = getTransactionsForAccount(account);

        List<FolioTransactionDto> dtoList = allTransactions.stream()
                .map(this::toDto)
                .filter(tx -> {
                    if (categoryFilter == null || categoryFilter.isBlank() || "ALL".equalsIgnoreCase(categoryFilter)) {
                        return true;
                    }
                    return tx.getCategory().equalsIgnoreCase(categoryFilter)
                            || tx.getItems().stream().anyMatch(i -> i.getCategory().equalsIgnoreCase(categoryFilter));
                })
                .sorted(Comparator.comparing(FolioTransactionDto::getTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        long offset = pageable.getOffset();
        if (offset >= dtoList.size()) {
            return new PageImpl<>(List.of(), pageable, dtoList.size());
        }
        int start = (int) offset;
        int end = Math.min(start + pageable.getPageSize(), dtoList.size());
        List<FolioTransactionDto> paged = dtoList.subList(start, end);

        return new PageImpl<>(paged, pageable, dtoList.size());
    }

    @Transactional
    public DisputeResponse disputeTransaction(Long passengerId, Long txId, CreateFolioDisputeRequest request) {
        CabinFolioAccount account = findAccountByPassengerId(passengerId);
        PosTransaction transaction = posTransactionRepository.findById(txId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch với ID: " + txId));

        if (!Objects.equals(transaction.getCabinNumber(), account.getCabinNumber())) {
            throw new BusinessRuleException("Giao dịch không thuộc về tài khoản cabin của hành khách");
        }

        if (transaction.getTotalAmount() == null) {
            throw new BusinessRuleException("Giao dịch không có tổng tiền hợp lệ");
        }

        BigDecimal disputeAmount = request.getDisputeAmount() != null ? request.getDisputeAmount() : transaction.getTotalAmount();
        if (disputeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Số tiền khiếu nại phải lớn hơn 0");
        }
        if (disputeAmount.compareTo(transaction.getTotalAmount()) > 0) {
            throw new BusinessRuleException("Số tiền khiếu nại không được vượt quá giá trị giao dịch (" + transaction.getTotalAmount() + ")");
        }

        String txIdentifier = transaction.getTransactionUuid() != null ? transaction.getTransactionUuid() : String.valueOf(transaction.getId());

        boolean activeDisputeExists = disputeCaseRepository.existsByTransactionIdAndStatusIn(
                txIdentifier,
                List.of(DisputeStatus.SUBMITTED, DisputeStatus.UNDER_INVESTIGATION, DisputeStatus.APPROVED_ADJUSTED)
        );
        if (activeDisputeExists) {
            throw new BusinessRuleException("Giao dịch này đang có khiếu nại đang xử lý hoặc đã hoàn tất");
        }

        String disputeNumber = "DISP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DisputeCase dispute = DisputeCase.builder()
                .disputeNumber(disputeNumber)
                .transactionId(txIdentifier)
                .folioId("FOLIO-" + account.getCabinNumber())
                .cabinNumber(account.getCabinNumber())
                .passengerId(String.valueOf(passengerId))
                .passengerName(account.getPrimaryGuestName())
                .reason(request.getReason())
                .disputeAmount(disputeAmount)
                .passengerNote(request.getDisputeNote())
                .status(DisputeStatus.SUBMITTED)
                .build();

        DisputeCase saved = disputeCaseRepository.save(dispute);

        DisputeHistory history = DisputeHistory.builder()
                .disputeId(saved.getId())
                .previousStatus(null)
                .newStatus(DisputeStatus.SUBMITTED)
                .actionBy("PASSENGER-" + passengerId)
                .notes("Hành khách gửi yêu cầu khiếu nại: " + request.getDisputeNote())
                .timestamp(Instant.now())
                .build();
        disputeHistoryRepository.save(history);

        log.info("Dispute created: {} for transaction {}", disputeNumber, txIdentifier);

        return DisputeResponse.builder()
                .disputeId(saved.getId())
                .disputeNumber(saved.getDisputeNumber())
                .transactionId(saved.getTransactionId())
                .passengerId(passengerId)
                .cabinNumber(saved.getCabinNumber())
                .reason(saved.getReason())
                .disputeAmount(saved.getDisputeAmount())
                .status(saved.getStatus())
                .passengerNote(saved.getPassengerNote())
                .createdAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : Instant.now())
                .build();
    }

    private CabinFolioAccount findAccountByPassengerId(Long passengerId) {
        return cabinFolioAccountRepository.findByPassengerId(passengerId)
                .orElseGet(() -> cabinFolioAccountRepository.findByCabinNumber("CABIN-" + passengerId)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Folio cho hành khách ID: " + passengerId)));
    }

    private List<PosTransaction> getTransactionsForAccount(CabinFolioAccount account) {
        List<PosTransaction> byCabin = posTransactionRepository.findByCabinNumber(account.getCabinNumber());
        if (byCabin != null && !byCabin.isEmpty()) {
            return byCabin;
        }
        return Collections.emptyList();
    }

    private Map<String, BigDecimal> initStandardBreakdown() {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (String cat : STANDARD_CATEGORIES) {
            map.put(cat, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        return map;
    }

    public static String mapToStandardCategory(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return CAT_FOOD_AND_BEVERAGE;
        }
        String upper = rawCategory.toUpperCase();
        if (upper.contains("FOOD") || upper.contains("BEVERAGE") || upper.contains("BAR") || upper.contains("RESTAURANT") || upper.contains("DRINK")) {
            return CAT_FOOD_AND_BEVERAGE;
        }
        if (upper.contains("EXCURSION") || upper.contains("TOUR") || upper.contains("SHORE")) {
            return CAT_SHORE_EXCURSIONS;
        }
        if (upper.contains("SPA") || upper.contains("WELLNESS") || upper.contains("MASSAGE") || upper.contains("FITNESS")) {
            return CAT_SPA_AND_WELLNESS;
        }
        if (upper.contains("RETAIL") || upper.contains("SHOP") || upper.contains("SOUVENIR") || upper.contains("BOUTIQUE")) {
            return CAT_RETAIL_SHOPPING;
        }
        if (upper.contains("FEE") || upper.contains("SERVICE") || upper.contains("GRATUITY") || upper.contains("SURCHARGE")) {
            return CAT_SERVICE_FEES;
        }
        return CAT_FOOD_AND_BEVERAGE;
    }

    private FolioTransactionDto toDto(PosTransaction tx) {
        List<FolioTransactionDto.ItemDto> items = new ArrayList<>();
        Map<String, BigDecimal> categoryTotals = new HashMap<>();

        if (tx.getItems() != null && !tx.getItems().isEmpty()) {
            for (PosTransactionItem item : tx.getItems()) {
                boolean isItemFree = item.getTotalPrice() == null || item.getTotalPrice().compareTo(BigDecimal.ZERO) == 0;
                String stdCat = mapToStandardCategory(item.getCategory());
                BigDecimal price = item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO;
                categoryTotals.put(stdCat, categoryTotals.getOrDefault(stdCat, BigDecimal.ZERO).add(price));

                items.add(FolioTransactionDto.ItemDto.builder()
                        .itemCode(item.getItemCode())
                        .itemName(item.getItemName())
                        .category(stdCat)
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO)
                        .totalPrice(price)
                        .complimentary(isItemFree)
                        .build());
            }
        }

        String dominantCategory = categoryTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseGet(() -> mapToStandardCategory(tx.getNotes()));

        boolean isTxFree = tx.getTotalAmount() == null || tx.getTotalAmount().compareTo(BigDecimal.ZERO) == 0;

        return FolioTransactionDto.builder()
                .id(tx.getId())
                .transactionUuid(tx.getTransactionUuid())
                .time(tx.getCreatedAt() != null ? tx.getCreatedAt() : Instant.now())
                .locationCounter(tx.getStationId())
                .posCode(tx.getCashierId())
                .swipedBy(tx.getGuestName() != null ? tx.getGuestName() : "Guest")
                .amount(tx.getTotalAmount() != null ? tx.getTotalAmount() : BigDecimal.ZERO)
                .currency(tx.getCurrency() != null ? tx.getCurrency() : "USD")
                .category(dominantCategory)
                .complimentary(isTxFree)
                .status(tx.getStatus() != null ? tx.getStatus().name() : "COMPLETED")
                .items(items)
                .build();
    }
}
