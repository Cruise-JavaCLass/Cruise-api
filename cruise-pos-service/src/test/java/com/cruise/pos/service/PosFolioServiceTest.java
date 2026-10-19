package com.cruise.pos.service;

import com.cruise.pos.dto.FolioAccountResponse;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.exception.InsufficientFolioBalanceException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PosFolioService Unit Tests")
class PosFolioServiceTest {

    @Mock
    private CabinFolioAccountRepository folioRepository;

    @InjectMocks
    private PosFolioService folioService;

    private CabinFolioAccount folio;

    @BeforeEach
    void setUp() {
        folio = CabinFolioAccount.builder()
                .cabinNumber("CABIN-7012")
                .primaryGuestName("Nguyễn Văn An")
                .passengerId(101L)
                .creditLimit(BigDecimal.valueOf(1500.00))
                .currentBalance(BigDecimal.valueOf(200.00))
                .status(FolioStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("getFolioByCabin returns account when found")
    void testGetFolioByCabinSuccess() {
        when(folioRepository.findByCabinNumber("CABIN-7012")).thenReturn(Optional.of(folio));

        FolioAccountResponse res = folioService.getFolioByCabin("CABIN-7012");
        assertThat(res.getCabinNumber()).isEqualTo("CABIN-7012");
        assertThat(res.getAvailableLimit()).isEqualByComparingTo(BigDecimal.valueOf(1300.00));
    }

    @Test
    @DisplayName("getFolioByCabin throws ResourceNotFoundException when missing")
    void testGetFolioByCabinNotFound() {
        when(folioRepository.findByCabinNumber("INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> folioService.getFolioByCabin("INVALID"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy tài khoản Folio");
    }

    @Test
    @DisplayName("chargeFolio debits balance successfully when limit allows")
    void testChargeFolioSuccess() {
        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(folio));

        folioService.chargeFolio("CABIN-7012", BigDecimal.valueOf(300.00));

        assertThat(folio.getCurrentBalance()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        verify(folioRepository).save(folio);
    }

    @Test
    @DisplayName("chargeFolio throws InsufficientFolioBalanceException when amount exceeds available limit")
    void testChargeFolioInsufficientBalance() {
        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(folio));

        assertThatThrownBy(() -> folioService.chargeFolio("CABIN-7012", BigDecimal.valueOf(2000.00)))
                .isInstanceOf(InsufficientFolioBalanceException.class)
                .hasMessageContaining("không đủ hạn mức tín dụng");

        verify(folioRepository, never()).save(any());
    }

    @Test
    @DisplayName("refundFolio restores balance successfully")
    void testRefundFolioSuccess() {
        when(folioRepository.findByCabinNumberWithLock("CABIN-7012")).thenReturn(Optional.of(folio));

        folioService.refundFolio("CABIN-7012", BigDecimal.valueOf(100.00));

        assertThat(folio.getCurrentBalance()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        verify(folioRepository).save(folio);
    }
}
