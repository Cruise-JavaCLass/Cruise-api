package com.cruise.pos.service;

import com.cruise.pos.entity.PosCashDrop;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Generates ESC/POS compatible thermal receipt text and formatting commands
 * for Mid-Shift Cash Drop Safe Slips with dual signature blocks (KAN-613).
 */
@Service
public class EscPosReceiptService {

    // Standard ESC/POS Command Constants
    public static final String ESC_INIT = "\u001B@";          // Initialize printer
    public static final String ESC_ALIGN_CENTER = "\u001Ba1"; // Align center
    public static final String ESC_ALIGN_LEFT = "\u001Ba0";   // Align left
    public static final String ESC_ALIGN_RIGHT = "\u001Ba2";  // Align right
    public static final String ESC_EMPHASIZE_ON = "\u001BE1"; // Bold on
    public static final String ESC_EMPHASIZE_OFF = "\u001BE0";// Bold off
    public static final String ESC_DOUBLE_HEIGHT = "\u001B!10";// Double height text
    public static final String ESC_NORMAL_TEXT = "\u001B!0";  // Normal text size
    public static final String ESC_CUT_PAPER = "\u001DV0";    // Cut paper full/partial

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));

    /**
     * Builds ESC/POS formatted thermal receipt string for mid-shift safe drop slip.
     */
    public String generateSafeDropSlip(PosCashDrop drop) {
        StringBuilder sb = new StringBuilder();

        sb.append(ESC_INIT);
        // Header
        sb.append(ESC_ALIGN_CENTER).append(ESC_EMPHASIZE_ON).append(ESC_DOUBLE_HEIGHT)
                .append("CRUISE SHIP SAFE TRANSFER\n")
                .append(ESC_NORMAL_TEXT).append("MID-SHIFT CASH DROP RECEIPT\n")
                .append("================================\n")
                .append(ESC_EMPHASIZE_OFF);

        // Details
        sb.append(ESC_ALIGN_LEFT);
        sb.append("Receipt No : ").append(sanitize(drop.getReceiptNumber())).append("\n");
        sb.append("Date/Time  : ").append(DATE_FORMATTER.format(drop.getDroppedAt())).append(" UTC\n");
        sb.append("Station ID : ").append(sanitize(drop.getStationId())).append("\n");
        sb.append("Shift ID   : #").append(drop.getShiftId()).append("\n");
        sb.append("Cashier    : ").append(sanitize(drop.getCashierId())).append("\n");
        sb.append("Witness    : ").append(sanitize(drop.getWitnessId())).append("\n");
        sb.append("--------------------------------\n");

        if (drop.getCurrencyBreakdown() != null && !drop.getCurrencyBreakdown().isBlank()) {
            sb.append("Breakdown  : ").append(sanitize(drop.getCurrencyBreakdown())).append("\n");
            sb.append("--------------------------------\n");
        }

        // Amount & Safe balance
        sb.append(ESC_EMPHASIZE_ON);
        sb.append("DROPPED AMOUNT : $").append(String.format(Locale.US, "%.2f", drop.getAmount())).append("\n");
        sb.append("SAFE BALANCE   : $").append(String.format(Locale.US, "%.2f", drop.getSafeBalanceAfter())).append("\n");
        sb.append(ESC_EMPHASIZE_OFF);
        sb.append("================================\n");

        // Dual Signatures
        sb.append(ESC_ALIGN_CENTER).append(ESC_EMPHASIZE_ON).append("DUAL SIGNATURE AUTHORIZATION\n").append(ESC_EMPHASIZE_OFF);
        sb.append(ESC_ALIGN_LEFT);
        sb.append("\n\n");
        sb.append("Cashier Sign: __________________\n");
        sb.append("Name: ").append(sanitize(drop.getCashierId())).append("\n\n");
        sb.append("Witness/Safe: __________________\n");
        sb.append("Name: ").append(sanitize(drop.getWitnessId())).append(" (Supervisor)\n");
        sb.append("--------------------------------\n");
        sb.append(ESC_ALIGN_CENTER);
        sb.append("*** KEEP SAFE RECEIPT FOR AUDIT ***\n\n\n\n");
        sb.append(ESC_CUT_PAPER);

        return sb.toString();
    }

    private String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("[\u0000-\u001F\u007F]", " ").trim();
    }
}
