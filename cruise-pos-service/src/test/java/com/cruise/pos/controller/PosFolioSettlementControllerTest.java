package com.cruise.pos.controller;

import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.FolioSettlementRepository;
import com.cruise.pos.repository.PosTransactionRepository;
import com.cruise.pos.support.SettlementTestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Settlement & invoice HTTP contract")
class PosFolioSettlementControllerTest {

    private static final String SETTLE_URL = "/api/pos/folios/%s/settle";
    private static final String PREVIEW_URL = "/api/pos/folios/%s/settlement-preview";
    private static final String INVOICE_URL = "/api/pos/folios/%s/invoice";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CabinFolioAccountRepository folioRepository;

    @Autowired
    private PosTransactionRepository transactionRepository;

    @Autowired
    private FolioSettlementRepository settlementRepository;

    private String cabin;

    @BeforeEach
    void setUp() {
        cabin = SettlementTestData.uniqueCabin("CABIN-HTTP");
        CabinFolioAccount folio = SettlementTestData.newFolio(folioRepository, cabin,
                "Trần Thị Mai", new BigDecimal("60.00"));
        SettlementTestData.saveCharge(transactionRepository, folio.getCabinNumber(),
                new BigDecimal("55.00"), new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "Bia lon", "BEVERAGE", 1, new BigDecimal("55.00"), TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Preview trả 200 kèm số dư phải thu")
    void previewReturnsOk() throws Exception {
        mockMvc.perform(get(String.format(PREVIEW_URL, cabin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cabinNumber").value(cabin))
                .andExpect(jsonPath("$.data.netAmountDue").value(60.00))
                .andExpect(jsonPath("$.data.eligibleForSettlement").value(true));
    }

    @Test
    @DisplayName("Preview cabin lạ trả 404")
    void previewUnknownCabinReturnsNotFound() throws Exception {
        mockMvc.perform(get(String.format(PREVIEW_URL, "CABIN-KHONG-CO")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tất toán lần đầu trả 201, replay cùng key trả 200 kèm header Idempotent-Replay")
    void settleThenReplay() throws Exception {
        String key = UUID.randomUUID().toString();
        String payload = settlePayload("CASH", "60.00", false);

        MvcResult first = mockMvc.perform(post(String.format(SETTLE_URL, cabin))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.invoiceNumber").exists())
                .andExpect(jsonPath("$.data.folioStatus").value("SETTLED"))
                .andReturn();

        String firstBody = first.getResponse().getContentAsString();

        MvcResult second = mockMvc.perform(post(String.format(SETTLE_URL, cabin))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andReturn();

        assertThat(second.getResponse().getContentAsString()).isEqualTo(firstBody);
        assertThat(settlementRepository.countByCabinNumber(cabin)).isEqualTo(1);
    }

    @Test
    @DisplayName("Thiếu Idempotency-Key trả 400")
    void settleWithoutIdempotencyKeyReturnsBadRequest() throws Exception {
        mockMvc.perform(post(String.format(SETTLE_URL, cabin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settlePayload("CASH", "60.00", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers
                        .containsString("IDEMPOTENCY_KEY_REQUIRED")));
    }

    @Test
    @DisplayName("Số tiền nhận thiếu trả 422")
    void insufficientTenderReturnsUnprocessable() throws Exception {
        mockMvc.perform(post(String.format(SETTLE_URL, cabin))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settlePayload("CASH", "10.00", false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers
                        .containsString("INSUFFICIENT_TENDER")));
    }

    @Test
    @DisplayName("Tất toán lần hai với key khác trả 409")
    void secondSettlementReturnsConflict() throws Exception {
        mockMvc.perform(post(String.format(SETTLE_URL, cabin))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settlePayload("CASH", "60.00", false)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(String.format(SETTLE_URL, cabin))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settlePayload("CASH", "60.00", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers
                        .containsString("FOLIO_ALREADY_SETTLED")));
    }

    @Test
    @DisplayName("Payload sai (thiếu phương thức thanh toán) trả 400")
    void invalidPayloadReturnsBadRequest() throws Exception {
        mockMvc.perform(post(String.format(SETTLE_URL, cabin))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountTendered\": 60.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Hoá đơn trước khi tất toán trả 409")
    void invoiceBeforeSettlementReturnsConflict() throws Exception {
        mockMvc.perform(get(String.format(INVOICE_URL, cabin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers
                        .containsString("INVOICE_NOT_AVAILABLE")));
    }

    @Test
    @DisplayName("Hoá đơn sau khi tất toán trả PDF")
    void invoiceAfterSettlementReturnsPdf() throws Exception {
        mockMvc.perform(post(String.format(SETTLE_URL, cabin))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settlePayload("CREDIT_CARD", "60.00", false)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get(String.format(INVOICE_URL, cabin)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().exists("X-Invoice-Number"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
        assertThat(new String(result.getResponse().getContentAsByteArray(), 0, 4))
                .isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Payload forceWithHold bị chặn khi không có role quản lý")
    void forceWithHoldRejectedForStaff() throws Exception {
        mockMvc.perform(post(String.format(SETTLE_URL, cabin))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settlePayload("CASH", "60.00", true)))
                .andExpect(status().isForbidden());
    }

    private String settlePayload(String paymentMethod, String amount, boolean forceWithHold) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "paymentMethod", paymentMethod,
                "amountTendered", new BigDecimal(amount),
                "forceWithHold", forceWithHold));
    }
}
