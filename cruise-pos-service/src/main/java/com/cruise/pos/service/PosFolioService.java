package com.cruise.pos.service;

import com.cruise.pos.dto.FolioAccountResponse;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.exception.InsufficientFolioBalanceException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosFolioService {

    private final CabinFolioAccountRepository folioRepository;

    @PostConstruct
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
    public FolioAccountResponse getFolioByCabin(String cabinNumber) {
        CabinFolioAccount account = folioRepository.findByCabinNumber(cabinNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản Folio cho cabin: " + cabinNumber));
        return toResponse(account);
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
    public void chargeFolio(String cabinNumber, BigDecimal amount) {
        CabinFolioAccount folio = folioRepository.findByCabinNumberWithLock(cabinNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản Folio cho cabin: " + cabinNumber));

        if (!folio.canDebit(amount)) {
            throw new InsufficientFolioBalanceException(String.format(
                    "Tài khoản cabin %s không đủ hạn mức tín dụng. Hạn mức còn lại: $%.2f, yêu cầu thanh toán: $%.2f",
                    cabinNumber, folio.getAvailableLimit(), amount
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
