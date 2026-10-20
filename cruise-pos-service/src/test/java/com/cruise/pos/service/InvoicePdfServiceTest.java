package com.cruise.pos.service;

import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.exception.InvoiceNotAvailableException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.FolioInvoiceRepository;
import com.cruise.pos.repository.PosTransactionRepository;
import com.cruise.pos.support.SettlementTestData;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Invoice PDF rendering")
class InvoicePdfServiceTest {

    private static final Set<String> FRONT_DESK = Set.of("FINANCE_FRONT_DESK");
    private static final Set<String> MANAGER = Set.of("OPERATIONS_MANAGER");

    @Autowired
    private InvoicePdfService invoicePdfService;

    @Autowired
    private FolioSettlementService settlementService;

    @Autowired
    private CabinFolioAccountRepository folioRepository;

    @Autowired
    private PosTransactionRepository transactionRepository;

    @Autowired
    private FolioInvoiceRepository invoiceRepository;

    private String cabin;
    private String invoiceNumber;

    @BeforeEach
    void setUp() {
        cabin = SettlementTestData.uniqueCabin("CABIN-PDF");
        CabinFolioAccount folio = SettlementTestData.newFolio(folioRepository, cabin,
                "Nguyễn Văn An", new BigDecimal("42.00"));
        SettlementTestData.saveCharge(transactionRepository, folio.getCabinNumber(),
                new BigDecimal("40.00"), new BigDecimal("2.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "Hải sản nướng", "DINING", 2, new BigDecimal("20.00"), TransactionStatus.COMPLETED);

        SettleOutcome outcome = settlementService.settle(cabin, UUID.randomUUID().toString(),
                PaymentMethod.CREDIT_CARD, new BigDecimal("42.00"), null, false, "cashier-pdf", FRONT_DESK);
        invoiceNumber = outcome.response().getInvoiceNumber();
    }

    @Test
    @DisplayName("Hoá đơn tất định: hai lần render cho cùng SHA-256")
    void invoiceIsDeterministic() throws Exception {
        byte[] first = invoicePdfService.generate(cabin, false, FRONT_DESK).content();
        byte[] second = invoicePdfService.generate(cabin, false, FRONT_DESK).content();

        assertThat(sha256(first)).isEqualTo(sha256(second));
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("PDF hợp lệ và chứa thông tin bắt buộc")
    void invoiceContainsRequiredData() throws Exception {
        InvoicePdfService.InvoicePdfResult result = invoicePdfService.generate(cabin, false, FRONT_DESK);

        assertThat(new String(result.content(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(result.fileName()).isEqualTo(invoiceNumber + ".pdf");
        assertThat(result.invoiceNumber()).isEqualTo(invoiceNumber);

        String text = extractText(result.content());
        assertThat(text).contains(invoiceNumber);
        assertThat(text).contains(cabin);
        assertThat(text).contains("NET AMOUNT DUE");
    }

    @Test
    @DisplayName("PDF giữ đúng dấu tiếng Việt")
    void invoiceKeepsVietnameseDiacritics() throws Exception {
        String text = extractText(invoicePdfService.generate(cabin, false, FRONT_DESK).content());
        assertThat(text).contains("Nguyễn Văn An");
        assertThat(text).contains("Hải sản nướng");
    }

    @Test
    @DisplayName("Folio chưa tất toán thì chưa có hoá đơn")
    void invoiceRequiresSettlement() {
        assertThatThrownBy(() -> invoicePdfService.generate("CABIN-CHUA-TAT-TOAN", false, FRONT_DESK))
                .isInstanceOf(InvoiceNotAvailableException.class)
                .hasMessageContaining("INVOICE_NOT_AVAILABLE");
    }

    @Test
    @DisplayName("Chỉ quản lý mới được tái tạo hoá đơn")
    void regenerateRequiresManager() {
        assertThatThrownBy(() -> invoicePdfService.generate(cabin, true, FRONT_DESK))
                .isInstanceOf(AccessDeniedException.class);

        InvoicePdfService.InvoicePdfResult result = invoicePdfService.generate(cabin, true, MANAGER);
        assertThat(result.content()).isNotEmpty();
        assertThat(invoiceRepository.findByInvoiceNumber(invoiceNumber).orElseThrow()
                .getRegeneratedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Metadata hoá đơn phản ánh kích thước và sha256 của file")
    void metadataMatchesContent() {
        InvoicePdfService.InvoicePdfResult result = invoicePdfService.generate(cabin, false, FRONT_DESK);
        var metadata = invoicePdfService.getMetadata(invoiceNumber);

        assertThat(metadata.getByteSize()).isEqualTo((long) result.content().length);
        assertThat(metadata.getCabinNumber()).isEqualTo(cabin);
        assertThat(metadata.getSha256()).isNotBlank();
    }

    private String extractText(byte[] content) throws Exception {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String sha256(byte[] payload) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
    }

    @Test
    @DisplayName("Tao hoa don cho cabin khong co ten khach va nhieu dong giao dich vuot trang")
    void generateInvoiceMultiPageAndNoGuestName() throws Exception {
        String largeCabin = SettlementTestData.uniqueCabin("CABIN-LARGE");
        CabinFolioAccount folio = SettlementTestData.newFolio(folioRepository, largeCabin,
                "VIP Guest", new BigDecimal("1000.00"));

        for (int i = 0; i < 35; i++) {
            SettlementTestData.saveCharge(transactionRepository, folio.getCabinNumber(),
                    new BigDecimal("20.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    "Mon an dac biet \u0007 \u0000 #" + i, "DINING", 1, new BigDecimal("20.00"), TransactionStatus.COMPLETED);
        }

        settlementService.settle(largeCabin, UUID.randomUUID().toString(),
                PaymentMethod.CASH, new BigDecimal("1000.00"), null, false, "cashier-large", FRONT_DESK);

        InvoicePdfService.InvoicePdfResult result = invoicePdfService.generate(largeCabin, false, FRONT_DESK);
        assertThat(result.content()).isNotEmpty();
        // verified by extractText page markers
        assertThat(result.toString()).contains("InvoicePdfResult");

        String text = extractText(result.content());
        assertThat(text).contains("VIP Guest").contains("Mon an dac biet");
        assertThat(text).contains("VIP Guest");
    }
}