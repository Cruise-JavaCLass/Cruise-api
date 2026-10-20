package com.cruise.pos.service;

import com.cruise.pos.dto.InvoiceMetadataDto;
import com.cruise.pos.dto.SettlementLineItemDto;
import com.cruise.pos.entity.FolioInvoice;
import com.cruise.pos.entity.FolioSettlement;
import com.cruise.pos.exception.InvoiceNotAvailableException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.FolioInvoiceRepository;
import com.cruise.pos.repository.FolioSettlementRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Sinh hoá đơn PDF từ một bản ghi tất toán.
 *
 * <p>Hoá đơn được thiết kế <b>tất định</b>: cùng một settlement luôn cho cùng nội dung
 * và cùng SHA-256 nhờ (1) không dùng thời gian hiện tại khi render, (2) cố định
 * document ID thay vì để PDFBox sinh ngẫu nhiên, (3) không lưu blob mà render lại
 * từ dữ liệu đã chốt tại thời điểm tất toán.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoicePdfService {

    private static final String FONT_RESOURCE = "fonts/NotoSans-Regular.ttf";
    private static final String CURRENCY = "USD";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT).withZone(ZoneId.of("Asia/Bangkok"));
    private static final Set<String> REGENERATE_ROLES = Set.of("OPERATIONS_MANAGER", "SYSTEM_ADMIN");

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float MARGIN = 48f;
    private static final float ROW_HEIGHT = 14f;
    private static final float BOTTOM_LIMIT = 72f;

    private static final float COL_DESCRIPTION = MARGIN;
    private static final float COL_AREA = 330f;
    private static final float COL_POSTED = 410f;
    private static final float COL_AMOUNT_RIGHT = PAGE_WIDTH - MARGIN;

    private final FolioSettlementRepository settlementRepository;
    private final FolioInvoiceRepository invoiceRepository;
    private final ObjectMapper objectMapper;

    /**
     * Sinh hoá đơn cho folio đã tất toán.
     */
    @Transactional
    public InvoicePdfResult generate(String cabinNumber, boolean regenerate, Set<String> roles) {
        if (regenerate && roles.stream().noneMatch(REGENERATE_ROLES::contains)) {
            throw new AccessDeniedException("Chỉ quản lý vận hành mới được tái tạo hoá đơn");
        }

        FolioSettlement settlement = settlementRepository
                .findFirstByCabinNumberOrderBySettledAtDesc(cabinNumber)
                .orElseThrow(() -> new InvoiceNotAvailableException(
                        "INVOICE_NOT_AVAILABLE: Folio cabin " + cabinNumber + " chưa được tất toán"));

        byte[] pdf = render(settlement);
        String sha256 = sha256(pdf);

        FolioInvoice invoice = invoiceRepository.findBySettlementUuid(settlement.getSettlementUuid())
                .map(existing -> updateExisting(existing, pdf, sha256, regenerate))
                .orElseGet(() -> invoiceRepository.saveAndFlush(FolioInvoice.builder()
                        .invoiceNumber(settlement.getInvoiceNumber())
                        .settlementUuid(settlement.getSettlementUuid())
                        .folioId(settlement.getFolioId())
                        .cabinNumber(settlement.getCabinNumber())
                        .sha256(sha256)
                        .byteSize((long) pdf.length)
                        .generatedAt(settlement.getSettledAt())
                        .regeneratedCount(regenerate ? 1 : 0)
                        .build()));

        log.info("Rendered invoice {} for cabin {} ({} bytes, sha {})",
                invoice.getInvoiceNumber(), cabinNumber, pdf.length, sha256);

        return new InvoicePdfResult(pdf, settlement.getInvoiceNumber() + ".pdf", invoice.getInvoiceNumber());
    }

    @Transactional(readOnly = true)
    public InvoiceMetadataDto getMetadata(String invoiceNumber) {
        FolioInvoice invoice = invoiceRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hoá đơn: " + invoiceNumber));
        return InvoiceMetadataDto.builder()
                .invoiceNumber(invoice.getInvoiceNumber())
                .settlementUuid(invoice.getSettlementUuid())
                .cabinNumber(invoice.getCabinNumber())
                .sha256(invoice.getSha256())
                .byteSize(invoice.getByteSize())
                .generatedAt(invoice.getGeneratedAt())
                .regeneratedCount(invoice.getRegeneratedCount())
                .build();
    }

    private FolioInvoice updateExisting(FolioInvoice existing, byte[] pdf, String sha256, boolean regenerate) {
        if (!existing.getSha256().equals(sha256)) {
            log.warn("Invoice {} content hash changed: stored={}, recomputed={}",
                    existing.getInvoiceNumber(), existing.getSha256(), sha256);
        }
        if (regenerate) {
            existing.setRegeneratedCount(existing.getRegeneratedCount() + 1);
            existing.setSha256(sha256);
            existing.setByteSize((long) pdf.length);
            return invoiceRepository.saveAndFlush(existing);
        }
        return existing;
    }

    // ---------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------

    private byte[] render(FolioSettlement settlement) {
        List<SettlementLineItemDto> lineItems = readLineItems(settlement);

        try (PDDocument document = new PDDocument();
             InputStream fontStream = new ClassPathResource(FONT_RESOURCE).getInputStream()) {

            PDType0Font font = PDType0Font.load(document, fontStream, true);
            applyDeterministicMetadata(document, settlement);

            Canvas canvas = new Canvas(document, font);
            try {
                drawHeader(canvas, settlement);

                canvas.ensureSpace(30);
                canvas.text(COL_DESCRIPTION, 9, "Description");
                canvas.text(COL_AREA, 9, "Area");
                canvas.text(COL_POSTED, 9, "Posted at");
                canvas.rightText(9, "Amount (USD)");
                canvas.y -= 4;
                canvas.line(MARGIN, canvas.y, COL_AMOUNT_RIGHT, canvas.y);
                canvas.y -= ROW_HEIGHT;

                for (SettlementLineItemDto item : lineItems) {
                    canvas.ensureSpace(ROW_HEIGHT * 2);
                    canvas.text(COL_DESCRIPTION, 8, truncate(item.getDescription(), 52));
                    canvas.text(COL_AREA, 8, truncate(item.getServiceArea(), 14));
                    canvas.text(COL_POSTED, 8,
                            item.getPostedAt() == null ? "" : TIMESTAMP_FORMAT.format(item.getPostedAt()));
                    canvas.rightText(8, money(item.getAmount()));
                    canvas.y -= ROW_HEIGHT;
                }

                canvas.y -= 10;
                canvas.ensureSpace(180);
                drawTotals(canvas, settlement);
                drawFooter(canvas, settlement);
            } finally {
                canvas.close();
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            document.save(buffer);
            return buffer.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Không thể sinh hoá đơn PDF cho cabin " + settlement.getCabinNumber(), ex);
        }
    }

    private void applyDeterministicMetadata(PDDocument document, FolioSettlement settlement) {
        PDDocumentInformation info = new PDDocumentInformation();
        info.setTitle("Invoice " + settlement.getInvoiceNumber());
        info.setAuthor("Cruise Line");
        info.setSubject("Bill settlement for cabin " + settlement.getCabinNumber());
        info.setCreator("Cruise Billing");
        info.setProducer("Cruise Billing");

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(settlement.getSettledAt().toEpochMilli());
        info.setCreationDate(calendar);
        info.setModificationDate(calendar);
        document.setDocumentInformation(info);

        // PDFBox sinh /ID ngẫu nhiên mỗi lần save; cố định theo settlement để bytes ổn định.
        byte[] digest = digest(settlement.getSettlementUuid());
        byte[] id = Arrays.copyOf(digest, 16);
        COSArray documentId = new COSArray();
        documentId.add(new COSString(id));
        documentId.add(new COSString(id));
        COSDocument cosDocument = document.getDocument();
        cosDocument.setDocumentID(documentId);
    }

    private void drawHeader(Canvas canvas, FolioSettlement settlement) throws IOException {
        canvas.text(MARGIN, 17, "CRUISE LINE — FOLIO INVOICE");
        canvas.y -= 26;
        canvas.text(MARGIN, 11, "Invoice No: " + settlement.getInvoiceNumber());
        canvas.y -= 16;
        canvas.text(MARGIN, 8, "Settlement: " + settlement.getSettlementUuid());
        canvas.y -= 14;
        canvas.text(MARGIN, 9, "Cabin: " + settlement.getCabinNumber());
        canvas.y -= 14;
        if (settlement.getPrimaryGuestName() != null) {
            canvas.text(MARGIN, 9, "Guest: " + settlement.getPrimaryGuestName());
            canvas.y -= 14;
        }
        canvas.text(MARGIN, 9, "Settled at: " + TIMESTAMP_FORMAT.format(settlement.getSettledAt()));
        canvas.y -= 18;
        canvas.line(MARGIN, canvas.y, COL_AMOUNT_RIGHT, canvas.y);
        canvas.y -= 18;
    }

    private void drawTotals(Canvas canvas, FolioSettlement settlement) throws IOException {
        float labelX = 330f;
        canvas.totalRow(labelX, "Subtotal", settlement.getSubtotal());
        canvas.totalRow(labelX, "Reversals", settlement.getReversalsTotal());
        canvas.totalRow(labelX, "Discount", settlement.getDiscount());
        canvas.totalRow(labelX, "Maritime tax", settlement.getTax());
        canvas.totalRow(labelX, "Gratuity", settlement.getGratuity());
        canvas.totalRow(labelX, "Held disputes", settlement.getHeldDisputeAmount());
        canvas.totalRow(labelX, "NET AMOUNT DUE", settlement.getNetAmount());
        canvas.totalRow(labelX, "Amount tendered", settlement.getAmountTendered());
        canvas.totalRow(labelX, "Change given", settlement.getChangeGiven());

        canvas.y -= 8;
        canvas.text(MARGIN, 9, "Payment method: " + settlement.getPaymentMethod());
        canvas.y -= 14;
        canvas.text(MARGIN, 9, "Cashier: " + settlement.getCashierId());
        canvas.y -= 14;
        canvas.text(MARGIN, 9, "Currency: " + CURRENCY);
    }

    private void drawFooter(Canvas canvas, FolioSettlement settlement) throws IOException {
        canvas.ensureSpace(40);
        canvas.y -= 10;
        canvas.text(MARGIN, 7,
                "This invoice was generated electronically and is valid without signature.");
        canvas.y -= 12;
        canvas.text(MARGIN, 7, "Invoice " + settlement.getInvoiceNumber()
                + " — settlement " + settlement.getSettlementUuid());
    }

    private List<SettlementLineItemDto> readLineItems(FolioSettlement settlement) {
        if (settlement.getLineItemsJson() == null || settlement.getLineItemsJson().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(settlement.getLineItemsJson(),
                    new TypeReference<List<SettlementLineItemDto>>() {
                    });
        } catch (IOException ex) {
            throw new IllegalStateException("Bảng kê tất toán không hợp lệ", ex);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private String money(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return String.format(Locale.ROOT, "%,.2f", value.setScale(2, RoundingMode.HALF_UP));
    }

    private byte[] digest(String payload) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 không khả dụng", ex);
        }
    }

    private String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 không khả dụng", ex);
        }
    }

    /**
     * Kết quả render hoá đơn.
     */
    public record InvoicePdfResult(byte[] content, String fileName, String invoiceNumber) {
    }

    /**
     * Bộ vẽ nội dung PDF, tự động sang trang khi hết chỗ.
     */
    private static final class Canvas implements AutoCloseable {

        private final PDDocument document;
        private final PDType0Font font;
        private PDPage page;
        private PDPageContentStream stream;
        private float y = PDRectangle.A4.getHeight() - MARGIN;

        Canvas(PDDocument document, PDType0Font font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        void ensureSpace(float needed) throws IOException {
            if (y - needed < BOTTOM_LIMIT) {
                newPage();
            }
        }

        void text(float x, float size, String value) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, y);
            stream.showText(value == null ? "" : value);
            stream.endText();
        }

        void rightText(float size, String value) throws IOException {
            String safe = value == null ? "" : value;
            float width = font.getStringWidth(safe) / 1000f * size;
            text(COL_AMOUNT_RIGHT - width, size, safe);
        }

        void totalRow(float labelX, String label, BigDecimal amount) throws IOException {
            ensureSpace(ROW_HEIGHT);
            text(labelX, 8, label);
            rightText(8, money(amount));
            y -= 13;
        }

        void line(float x1, float y1, float x2, float y2) throws IOException {
            stream.setLineWidth(0.5f);
            stream.moveTo(x1, y1);
            stream.lineTo(x2, y2);
            stream.stroke();
        }

        String money(BigDecimal amount) {
            BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
            return String.format(Locale.ROOT, "%,.2f", value.setScale(2, RoundingMode.HALF_UP));
        }

        @Override
        public void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}
