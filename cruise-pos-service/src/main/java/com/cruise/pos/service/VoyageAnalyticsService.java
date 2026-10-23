package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.PosAuditLog;
import com.cruise.pos.entity.VoyageFinancialClosure;
import com.cruise.pos.enums.VoyageFinancialStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.DuplicateResourceException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.PosAuditLogRepository;
import com.cruise.pos.repository.VoyageFinancialClosureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoyageAnalyticsService {

    private final VoyageFinancialClosureRepository closureRepository;
    private final PosAuditLogRepository auditLogRepository;

    @Transactional
    public VoyageFinancialClosureDto initiateVoyageClosure(InitiateVoyageClosureRequest request, String actor) {
        log.info("Initiating voyage financial closure for voyageNumber={}, shipId={}, actor={}",
                request.voyageNumber(), request.shipId(), actor);

        if (closureRepository.findByVoyageNumber(request.voyageNumber()).isPresent()) {
            throw new DuplicateResourceException("Financial closure record for voyage " + request.voyageNumber() + " already exists");
        }

        if (!request.voyageEndDate().isAfter(request.voyageStartDate())) {
            throw new BusinessRuleException("voyageEndDate must be after voyageStartDate");
        }
        if (request.actualPassengerAttendance() > request.totalPassengerCapacity()) {
            throw new BusinessRuleException("actualPassengerAttendance cannot exceed totalPassengerCapacity");
        }

        long days = Math.max(1, Duration.between(request.voyageStartDate(), request.voyageEndDate()).toDays());
        int durationDays = (int) days;

        int apcd = request.actualPassengerAttendance() * durationDays; // Available Passenger Cruise Days

        // Sum Department Revenues
        BigDecimal revBars = defaultZero(request.revenueBeverageBars());
        BigDecimal revDining = defaultZero(request.revenueSpecialtyDining());
        BigDecimal revExcursions = defaultZero(request.revenueShoreExcursions());
        BigDecimal revSpa = defaultZero(request.revenueSpaWellness());
        BigDecimal revRetail = defaultZero(request.revenueRetailBoutiques());

        BigDecimal actualGross = revBars.add(revDining).add(revExcursions).add(revSpa).add(revRetail);
        BigDecimal discounts = defaultZero(request.totalDiscountsAndObc());
        BigDecimal actualNet = actualGross.subtract(discounts);
        if (actualNet.compareTo(BigDecimal.ZERO) < 0) {
            actualNet = BigDecimal.ZERO;
        }

        // Occupancy Percentage = (actual / capacity) * 100
        BigDecimal occupancy = BigDecimal.valueOf(request.actualPassengerAttendance())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(request.totalPassengerCapacity()), 2, RoundingMode.HALF_UP);

        // RevPCD = actualGrossRevenue / APCD (zero APCD -> zero RevPCD)
        BigDecimal revPcd = BigDecimal.ZERO;
        if (apcd > 0) {
            revPcd = actualGross.divide(BigDecimal.valueOf(apcd), 2, RoundingMode.HALF_UP);
        }

        // Budget Variance = ((actual - budget) / budget) * 100
        BigDecimal budgetVariance = BigDecimal.ZERO;
        if (request.budgetedGrossRevenue().compareTo(BigDecimal.ZERO) > 0) {
            budgetVariance = actualGross.subtract(request.budgetedGrossRevenue())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(request.budgetedGrossRevenue(), 2, RoundingMode.HALF_UP);
        }

        // Profitability Index = (actualNet / budgetedGross) * (occupancy / 100)
        BigDecimal profitabilityIndex = BigDecimal.ONE;
        if (request.budgetedGrossRevenue().compareTo(BigDecimal.ZERO) > 0) {
            profitabilityIndex = actualNet.divide(request.budgetedGrossRevenue(), 4, RoundingMode.HALF_UP)
                    .multiply(occupancy.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        VoyageFinancialClosure closure = VoyageFinancialClosure.builder()
                .voyageNumber(request.voyageNumber())
                .shipId(request.shipId())
                .itineraryName(request.itineraryName())
                .voyageStartDate(request.voyageStartDate())
                .voyageEndDate(request.voyageEndDate())
                .voyageDurationDays(durationDays)
                .totalPassengerCapacity(request.totalPassengerCapacity())
                .actualPassengerAttendance(request.actualPassengerAttendance())
                .passengerCruiseDays(apcd)
                .budgetedGrossRevenue(request.budgetedGrossRevenue())
                .actualGrossRevenue(actualGross)
                .actualNetRevenue(actualNet)
                .totalDiscountsAndObc(discounts)
                .revenueBeverageBars(revBars)
                .revenueSpecialtyDining(revDining)
                .revenueShoreExcursions(revExcursions)
                .revenueSpaWellness(revSpa)
                .revenueRetailBoutiques(revRetail)
                .occupancyPercentage(occupancy)
                .revenuePerPassengerCruiseDay(revPcd)
                .budgetVariancePercentage(budgetVariance)
                .profitabilityIndex(profitabilityIndex)
                .status(VoyageFinancialStatus.CLOSURE_IN_PROGRESS)
                .closureAuditNotes(request.closureAuditNotes())
                .build();

        VoyageFinancialClosure saved = closureRepository.save(closure);

        recordAudit(
                "VOYAGE_CLOSURE_INITIATED",
                actor,
                "Initiated financial closure for voyage " + request.voyageNumber() + ". Actual Gross: $" + actualGross,
                "RevPCD=" + revPcd + ", Occupancy=" + occupancy + "%"
        );

        return toDto(saved);
    }

    @Transactional
    public VoyageFinancialClosureDto finalizeVoyageClosure(String voyageNumber, FinalizeVoyageClosureRequest request, String closedBy) {
        log.info("Finalizing voyage closure for voyageNumber={}, closedBy={}", voyageNumber, closedBy);
        VoyageFinancialClosure closure = findByVoyageNumberOrThrow(voyageNumber);

        if (closure.getStatus() == VoyageFinancialStatus.FINANCIALLY_CLOSED || 
            closure.getStatus() == VoyageFinancialStatus.AUDITED) {
            throw new BusinessRuleException("Voyage " + voyageNumber + " is already financially closed.");
        }

        closure.setStatus(VoyageFinancialStatus.FINANCIALLY_CLOSED);
        closure.setClosedBy(closedBy);
        closure.setClosedAt(Instant.now());
        closure.setClosureAuditNotes(request.closureAuditNotes());

        VoyageFinancialClosure saved = closureRepository.save(closure);

        recordAudit(
                "VOYAGE_FINANCIALLY_CLOSED",
                closedBy,
                "Voyage " + voyageNumber + " officially closed and audited.",
                "ActualNet=" + saved.getActualNetRevenue() + ", Status=FINANCIALLY_CLOSED"
        );

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<VoyageFinancialClosureDto> searchVoyages(
            VoyageFinancialStatus status,
            String shipId,
            String search,
            Pageable pageable) {
        return closureRepository.searchVoyages(status, shipId, search, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public VoyageFinancialClosureDto getVoyageClosure(String voyageNumber) {
        return toDto(findByVoyageNumberOrThrow(voyageNumber));
    }

    @Transactional(readOnly = true)
    public ExecutiveVoyageAnalyticsSummary getExecutiveSummary() {
        List<VoyageFinancialClosure> recent = closureRepository.findRecentClosedVoyages(
                List.of(VoyageFinancialStatus.FINANCIALLY_CLOSED, VoyageFinancialStatus.AUDITED),
                PageRequest.of(0, 10)
        );

        if (recent.isEmpty()) {
            return new ExecutiveVoyageAnalyticsSummary(
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    List.of()
            );
        }

        VoyageFinancialClosure latest = recent.get(0);

        BigDecimal sumRevPcd = BigDecimal.ZERO;
        BigDecimal sumOccupancy = BigDecimal.ZERO;
        BigDecimal sumGross = BigDecimal.ZERO;

        for (VoyageFinancialClosure v : recent) {
            sumRevPcd = sumRevPcd.add(v.getRevenuePerPassengerCruiseDay());
            sumOccupancy = sumOccupancy.add(v.getOccupancyPercentage());
            sumGross = sumGross.add(v.getActualGrossRevenue());
        }

        BigDecimal count = BigDecimal.valueOf(recent.size());
        BigDecimal avgRevPcd = sumRevPcd.divide(count, 2, RoundingMode.HALF_UP);
        BigDecimal avgOccupancy = sumOccupancy.divide(count, 2, RoundingMode.HALF_UP);

        return new ExecutiveVoyageAnalyticsSummary(
                toDto(latest),
                avgRevPcd,
                avgOccupancy,
                sumGross,
                recent.stream().map(this::toDto).toList()
        );
    }

    public byte[] generateExecutivePdfBooklet(String voyageNumber) {
        VoyageFinancialClosure v = findByVoyageNumberOrThrow(voyageNumber);

        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.setLeading(16f);
                cs.newLineAtOffset(50, 750);

                cs.showText("CRUISE LINE EXECUTIVE POST-VOYAGE FINANCIAL & OPERATIONAL AUDIT BOOKLET");
                cs.newLine();
                cs.showText("Voyage: " + sanitizePdfText(v.getVoyageNumber()) + " | Ship: " + sanitizePdfText(v.getShipId()));
                cs.newLine();
                cs.showText("Itinerary: " + sanitizePdfText(v.getItineraryName()));
                cs.newLine();
                cs.showText("Duration: " + v.getVoyageDurationDays() + " days | Capacity: " + v.getTotalPassengerCapacity() + " | Attendance: " + v.getActualPassengerAttendance());
                cs.newLine();
                cs.showText("Occupancy: " + v.getOccupancyPercentage() + "% | RevPCD: $" + v.getRevenuePerPassengerCruiseDay());
                cs.newLine();
                cs.showText("Budget Gross: $" + v.getBudgetedGrossRevenue() + " | Actual Gross: $" + v.getActualGrossRevenue());
                cs.newLine();
                cs.showText("Net Revenue: $" + v.getActualNetRevenue() + " | Variance: " + v.getBudgetVariancePercentage() + "%");
                cs.newLine();
                cs.showText("Profitability Index: " + v.getProfitabilityIndex());
                cs.newLine();
                cs.showText("Status: " + v.getStatus() + " | Closed By: " + sanitizePdfText(v.getClosedBy() != null ? v.getClosedBy() : "N/A"));
                cs.newLine();
                cs.showText("Department Breakdown: Bars=$" + v.getRevenueBeverageBars()
                        + ", Dining=$" + v.getRevenueSpecialtyDining()
                        + ", Excursions=$" + v.getRevenueShoreExcursions()
                        + ", Spa=$" + v.getRevenueSpaWellness()
                        + ", Boutiques=$" + v.getRevenueRetailBoutiques());
                cs.endText();
            }

            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate executive PDF booklet for voyage: " + voyageNumber, e);
        }
    }

    @Transactional
    public void dispatchExecutiveEmail(String voyageNumber, DispatchExecutiveSummaryEmailRequest request, String actor) {
        VoyageFinancialClosure closure = findByVoyageNumberOrThrow(voyageNumber);
        log.info("Executive booklet email queued for dispatch to {} for voyage {}", request.recipientEmail(), voyageNumber);
        recordAudit(
                "EXECUTIVE_BOOKLET_EMAILED",
                actor,
                "Dispatched executive summary booklet for voyage " + voyageNumber + " to " + request.recipientEmail(),
                "cc=" + (request.ccEmails() != null ? String.join(",", request.ccEmails()) : "none")
        );
    }

    private String sanitizePdfText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\r\\n]+", " ").trim();
    }

    private VoyageFinancialClosure findByVoyageNumberOrThrow(String voyageNumber) {
        return closureRepository.findByVoyageNumber(voyageNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Voyage financial closure not found: " + voyageNumber));
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private void recordAudit(String action, String actor, String reason, String details) {
        PosAuditLog logEntry = PosAuditLog.builder()
                .action(action)
                .performedBy(actor != null ? actor : "EXECUTIVE_CONTROLLER")
                .reason(reason)
                .details(details)
                .build();
        auditLogRepository.save(logEntry);
    }

    private VoyageFinancialClosureDto toDto(VoyageFinancialClosure v) {
        return new VoyageFinancialClosureDto(
                v.getId(),
                v.getVoyageNumber(),
                v.getShipId(),
                v.getItineraryName(),
                v.getVoyageStartDate(),
                v.getVoyageEndDate(),
                v.getVoyageDurationDays(),
                v.getTotalPassengerCapacity(),
                v.getActualPassengerAttendance(),
                v.getPassengerCruiseDays(),
                v.getBudgetedGrossRevenue(),
                v.getActualGrossRevenue(),
                v.getActualNetRevenue(),
                v.getTotalDiscountsAndObc(),
                v.getRevenueBeverageBars(),
                v.getRevenueSpecialtyDining(),
                v.getRevenueShoreExcursions(),
                v.getRevenueSpaWellness(),
                v.getRevenueRetailBoutiques(),
                v.getOccupancyPercentage(),
                v.getRevenuePerPassengerCruiseDay(),
                v.getBudgetVariancePercentage(),
                v.getProfitabilityIndex(),
                v.getStatus(),
                v.getClosedBy(),
                v.getClosedAt(),
                v.getClosureAuditNotes(),
                v.getCreatedAt() != null ? v.getCreatedAt() : Instant.now()
        );
    }
}
