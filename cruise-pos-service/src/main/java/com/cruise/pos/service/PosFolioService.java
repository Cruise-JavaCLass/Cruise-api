package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.entity.GuestFolioLedger;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.DuplicateResourceException;
import com.cruise.pos.exception.InsufficientFolioBalanceException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.GuestFolioLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosFolioService {

    private final CabinFolioAccountRepository folioRepository;
    private final GuestFolioLedgerRepository guestFolioLedgerRepository;

    public void seedDefaultFolios() {
        seedFolioIfAbsent("CABIN-7012", "Nguyễn Văn An", 101L, BigDecimal.valueOf(2000.00));
        seedFolioIfAbsent("CABIN-8015", "Trần Thị Mai", 102L, BigDecimal.valueOf(1500.00));
        seedFolioIfAbsent("CABIN-9020", "Lê Hoàng Long", 103L, BigDecimal.valueOf(3000.00));
    }

    private void seedFolioIfAbsent(String cabinNumber, String guestName, Long passengerId, BigDecimal limit) {
        if (folioRepository.findByCabinNumber(cabinNumber).isEmpty()) {
            CabinFolioAccount account = CabinFolioAccount.builder()
                    .cabinNumber(cabinNumber)
                    .primaryGuestName(guestName)
                    .passengerId(passengerId)
                    .creditLimit(limit)
                    .currentBalance(BigDecimal.ZERO)
                    .status(FolioStatus.ACTIVE)
                    .build();
            folioRepository.save(account);
            log.info("Seeded default cabin folio: {}", cabinNumber);
        }
    }

    @Transactional(readOnly = true)
    public Page<FolioAccountResponse> getFolios(String search, FolioStatus status, Pageable pageable) {
        String query = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        return folioRepository.findWithFilter(query, status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FolioAccountResponse getFolioByCabin(String cabinNumber) {
        CabinFolioAccount account = folioRepository.findByCabinNumber(cabinNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản Folio cho cabin: " + cabinNumber));
        return toResponse(account);
    }

    @Transactional
    public FolioAccountResponse createFolio(CreateFolioRequest request) {
        if (folioRepository.findByCabinNumber(request.getCabinNumber()).isPresent()) {
            throw new DuplicateResourceException("Tài khoản Folio cho cabin " + request.getCabinNumber() + " đã tồn tại");
        }

        CabinFolioAccount account = CabinFolioAccount.builder()
                .cabinNumber(request.getCabinNumber())
                .primaryGuestName(request.getPrimaryGuestName())
                .passengerId(request.getPassengerId())
                .creditLimit(request.getCreditLimit())
                .currentBalance(BigDecimal.ZERO)
                .status(FolioStatus.ACTIVE)
                .build();

        CabinFolioAccount saved = folioRepository.save(account);
        log.info("Created new cabin folio account: cabin={}, guest={}, limit=${}",
                saved.getCabinNumber(), saved.getPrimaryGuestName(), saved.getCreditLimit());
        return toResponse(saved);
    }

    @Transactional
    public CabinFolioAccount getOrCreateFolio(String cabinNumber, String guestName, Long passengerId) {
        return folioRepository.findByCabinNumber(cabinNumber)
                .orElseGet(() -> folioRepository.save(CabinFolioAccount.builder()
                        .cabinNumber(cabinNumber)
                        .primaryGuestName(guestName != null ? guestName : "Khách phòng " + cabinNumber)
                        .passengerId(passengerId)
                        .creditLimit(BigDecimal.valueOf(1500.00))
                        .currentBalance(BigDecimal.ZERO)
                        .status(FolioStatus.ACTIVE)
                        .build()));
    }

    @Transactional
    public FolioAccountResponse updateCreditLimit(String cabinNumber, UpdateCreditLimitRequest request) {
        CabinFolioAccount folio = folioRepository.findByCabinNumberWithLock(cabinNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản Folio cho cabin: " + cabinNumber));

        if (request.getCreditLimit().compareTo(folio.getCurrentBalance()) < 0) {
            throw new BusinessRuleException(String.format(
                    "Hạn mức mới ($%.2f) không thể nhỏ hơn số dư nợ hiện tại ($%.2f)",
                    request.getCreditLimit(), folio.getCurrentBalance()
            ));
        }

        BigDecimal oldLimit = folio.getCreditLimit();
        folio.setCreditLimit(request.getCreditLimit());
        CabinFolioAccount updated = folioRepository.save(folio);
        log.info("Updated credit limit for cabin {}: ${} -> ${}, Reason: {}",
                cabinNumber, oldLimit, updated.getCreditLimit(), request.getReason());
        return toResponse(updated);
    }

    @Transactional
    public FolioAccountResponse updateStatus(String cabinNumber, UpdateFolioStatusRequest request) {
        CabinFolioAccount folio = folioRepository.findByCabinNumberWithLock(cabinNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản Folio cho cabin: " + cabinNumber));

        folio.setStatus(request.getStatus());
        CabinFolioAccount updated = folioRepository.save(folio);
        log.info("Updated status for cabin {} to {}. Reason: {}", cabinNumber, request.getStatus(), request.getReason());
        return toResponse(updated);
    }

    @Transactional
    public void chargeFolio(String cabinNumber, BigDecimal amount) {
        CabinFolioAccount folio = folioRepository.findByCabinNumberWithLock(cabinNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản Folio cho cabin: " + cabinNumber));

        if (folio.getStatus() == FolioStatus.LOCKED) {
            throw new FolioNotActiveException(
                    "FOLIO_LOCKED: Folio cabin " + cabinNumber + " đang bị khoá");
        }
        if (folio.getStatus() != FolioStatus.ACTIVE) {
            throw new FolioNotActiveException(
                    "FOLIO_NOT_ACTIVE: Folio cabin " + cabinNumber + " đã tất toán, không thể ghi nợ thêm");
        }

        if (!folio.canDebit(amount)) {
            throw new InsufficientFolioBalanceException(String.format(
                    "Tài khoản cabin %s không đủ hạn mức tín dụng hoặc tài khoản bị khóa. Hạn mức còn lại: $%.2f, yêu cầu thanh toán: $%.2f, trạng thái: %s",
                    cabinNumber, folio.getAvailableLimit(), amount, folio.getStatus()
            ));
        }

        folio.debit(amount);
        folioRepository.save(folio);
        log.info("Debited ${} from cabin {}. New balance: ${}, available: ${}",
                amount, cabinNumber, folio.getCurrentBalance(), folio.getAvailableLimit());
    }

    @Transactional
    public void refundFolio(String cabinNumber, BigDecimal amount) {
        Optional<CabinFolioAccount> opt = folioRepository.findByCabinNumberWithLock(cabinNumber);
        if (opt.isPresent()) {
            CabinFolioAccount folio = opt.get();
            folio.credit(amount);
            folioRepository.save(folio);
            log.info("Refunded ${} to cabin {}. New balance: ${}", amount, cabinNumber, folio.getCurrentBalance());
        }
    }

    @Transactional
    public FolioLedgerItemDto postExpense(String cabinNumber, PostExpenseRequest request) {
        CabinFolioAccount folio = folioRepository.findByCabinNumberWithLock(cabinNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản Folio cho cabin: " + cabinNumber));

        if (guestFolioLedgerRepository.existsByTransactionId(request.getTransactionId())) {
            throw new DuplicateResourceException("Giao dịch " + request.getTransactionId() + " đã được ghi sổ trước đó");
        }

        boolean isReversal = Boolean.TRUE.equals(request.getIsReversal());
        if (isReversal) {
            folio.credit(request.getAmount());
        } else {
            if (!folio.canDebit(request.getAmount())) {
                throw new InsufficientFolioBalanceException(String.format(
                        "Tài khoản cabin %s không đủ hạn mức tín dụng hoặc tài khoản bị khóa. Hạn mức còn lại: $%.2f, yêu cầu thanh toán: $%.2f, trạng thái: %s",
                        cabinNumber, folio.getAvailableLimit(), request.getAmount(), folio.getStatus()
                ));
            }
            folio.debit(request.getAmount());
        }
        folioRepository.save(folio);

        GuestFolioLedger ledger = GuestFolioLedger.builder()
                .folioId("FOLIO-" + cabinNumber)
                .cabinNumber(cabinNumber)
                .passengerName(folio.getPrimaryGuestName())
                .transactionId(request.getTransactionId())
                .description(request.getDescription())
                .serviceArea(request.getServiceArea())
                .amount(request.getAmount())
                .isReversal(isReversal)
                .postedAt(Instant.now())
                .build();

        GuestFolioLedger savedLedger = guestFolioLedgerRepository.save(ledger);
        log.info("Posted expense to cabin {}: tx={}, area={}, amount=${}, isReversal={}",
                cabinNumber, savedLedger.getTransactionId(), savedLedger.getServiceArea(), savedLedger.getAmount(), isReversal);

        return FolioLedgerItemDto.builder()
                .id(savedLedger.getId())
                .transactionId(savedLedger.getTransactionId())
                .description(savedLedger.getDescription())
                .serviceArea(savedLedger.getServiceArea())
                .amount(savedLedger.getAmount())
                .isReversal(savedLedger.getIsReversal())
                .postedAt(savedLedger.getPostedAt())
                .runningBalance(folio.getCurrentBalance())
                .build();
    }

    @Transactional(readOnly = true)
    public FolioStatementResponse getFolioStatement(String cabinNumber, String serviceArea) {
        CabinFolioAccount account = folioRepository.findByCabinNumber(cabinNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản Folio cho cabin: " + cabinNumber));

        List<GuestFolioLedger> entries;
        if (serviceArea != null && !serviceArea.trim().isEmpty()) {
            entries = guestFolioLedgerRepository.findByCabinNumberAndServiceAreaOrderByPostedAtAsc(cabinNumber, serviceArea.trim());
        } else {
            entries = guestFolioLedgerRepository.findByCabinNumberOrderByPostedAtAsc(cabinNumber);
        }

        BigDecimal running = BigDecimal.ZERO;
        BigDecimal totalCharges = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        List<FolioLedgerItemDto> itemDtos = new ArrayList<>();

        for (GuestFolioLedger entry : entries) {
            if (Boolean.TRUE.equals(entry.getIsReversal())) {
                totalCredits = totalCredits.add(entry.getAmount());
                running = running.subtract(entry.getAmount());
            } else {
                totalCharges = totalCharges.add(entry.getAmount());
                running = running.add(entry.getAmount());
            }

            FolioLedgerItemDto item = FolioLedgerItemDto.builder()
                    .id(entry.getId())
                    .transactionId(entry.getTransactionId())
                    .description(entry.getDescription())
                    .serviceArea(entry.getServiceArea())
                    .amount(entry.getAmount())
                    .isReversal(entry.getIsReversal())
                    .postedAt(entry.getPostedAt())
                    .runningBalance(running.max(BigDecimal.ZERO))
                    .build();
            itemDtos.add(item);
        }

        return FolioStatementResponse.builder()
                .id(account.getId())
                .cabinNumber(account.getCabinNumber())
                .primaryGuestName(account.getPrimaryGuestName())
                .passengerId(account.getPassengerId())
                .creditLimit(account.getCreditLimit())
                .currentBalance(account.getCurrentBalance())
                .availableLimit(account.getAvailableLimit())
                .status(account.getStatus())
                .totalCharges(totalCharges)
                .totalCredits(totalCredits)
                .netBalance(account.getCurrentBalance())
                .items(itemDtos)
                .build();
    }

    public FolioAccountResponse toResponse(CabinFolioAccount account) {
        return FolioAccountResponse.builder()
                .id(account.getId())
                .cabinNumber(account.getCabinNumber())
                .primaryGuestName(account.getPrimaryGuestName())
                .passengerId(account.getPassengerId())
                .creditLimit(account.getCreditLimit())
                .currentBalance(account.getCurrentBalance())
                .availableLimit(account.getAvailableLimit())
                .status(account.getStatus())
                .build();
    }
}
