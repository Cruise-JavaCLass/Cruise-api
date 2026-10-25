package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.*;
import com.cruise.pos.enums.*;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceRecoveryService {

    private final ServiceRecoveryCaseRepository caseRepository;
    private final ServiceRecoveryObcVoucherRepository voucherRepository;
    private final CabinFolioAccountRepository folioRepository;
    private final GuestFolioLedgerRepository guestFolioLedgerRepository;
    private final PosAuditLogRepository auditLogRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public ServiceRecoveryCaseDto createCase(CreateServiceRecoveryCaseRequest request, String actor) {
        log.info("Creating service recovery case for cabin={}, passenger={}, rootCause={}, severity={}",
                request.cabinNumber(), request.passengerName(), request.rootCause(), request.severity());

        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String caseNumber = null;
        for (int attempts = 0; attempts < 50; attempts++) {
            String candidate = "SRC-" + datePrefix + "-" + (1000 + secureRandom.nextInt(9000));
            if (!caseRepository.existsByCaseNumber(candidate)) {
                caseNumber = candidate;
                break;
            }
        }
        if (caseNumber == null) {
            throw new IllegalStateException("Exhausted unique case number attempts for date " + datePrefix);
        }

        Instant now = Instant.now();
        Instant slaDueAt = computeSlaDueDate(request.severity(), now);

        ServiceRecoveryCase recoveryCase = ServiceRecoveryCase.builder()
                .caseNumber(caseNumber)
                .passengerId(request.passengerId())
                .passengerName(request.passengerName())
                .cabinNumber(request.cabinNumber())
                .rootCause(request.rootCause())
                .severity(request.severity())
                .status(ServiceRecoveryCaseStatus.OPEN)
                .complaintSummary(request.complaintSummary())
                .complaintDetails(request.complaintDetails())
                .assignedHandler(request.assignedHandler() != null ? request.assignedHandler() : "GUEST_RELATIONS_OFFICER")
                .slaDueAt(slaDueAt)
                .build();

        // Auto-escalate CRITICAL cases directly to Guest Relations Director
        if (request.severity() == ServiceRecoverySeverity.CRITICAL_DIRECTOR_ESCALATION) {
            recoveryCase.setStatus(ServiceRecoveryCaseStatus.ESCALATED);
            recoveryCase.setEscalatedAt(now);
            recoveryCase.setEscalationReason("Auto-escalated upon creation due to CRITICAL severity");
            log.warn("🚨 Service Recovery Case {} automatically escalated to Director level", caseNumber);
        }

        ServiceRecoveryCase saved = caseRepository.save(recoveryCase);

        recordAudit(
                "SERVICE_RECOVERY_CREATED",
                actor,
                "Created case " + caseNumber + " (" + request.rootCause() + ") for cabin " + request.cabinNumber(),
                "caseNumber=" + caseNumber + ", severity=" + request.severity()
        );

        return toCaseDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<ServiceRecoveryCaseDto> searchCases(
            ServiceRecoveryCaseStatus status,
            ServiceRecoverySeverity severity,
            String search,
            Pageable pageable) {
        return caseRepository.searchCases(status, severity, search, pageable)
                .map(this::toCaseDto);
    }

    @Transactional(readOnly = true)
    public ServiceRecoveryCaseDto getCase(Long caseId) {
        return toCaseDto(findCaseOrThrow(caseId));
    }

    @Transactional
    public ServiceRecoveryCaseDto escalateCase(Long caseId, EscalateCaseRequest request, String actor) {
        log.warn("Manually escalating service recovery caseId={}, actor={}", caseId, actor);
        ServiceRecoveryCase recoveryCase = findCaseOrThrow(caseId);

        if (recoveryCase.getStatus() == ServiceRecoveryCaseStatus.RESOLVED || recoveryCase.getStatus() == ServiceRecoveryCaseStatus.CLOSED) {
            throw new IllegalStateException("Cannot escalate case in status: " + recoveryCase.getStatus());
        }

        recoveryCase.setStatus(ServiceRecoveryCaseStatus.ESCALATED);
        recoveryCase.setSeverity(ServiceRecoverySeverity.CRITICAL_DIRECTOR_ESCALATION);
        Instant now = Instant.now();
        Instant criticalSla = computeSlaDueDate(ServiceRecoverySeverity.CRITICAL_DIRECTOR_ESCALATION, now);
        if (recoveryCase.getSlaDueAt() == null || criticalSla.isBefore(recoveryCase.getSlaDueAt())) {
            recoveryCase.setSlaDueAt(criticalSla);
        }
        recoveryCase.setEscalatedAt(now);
        recoveryCase.setEscalationReason(request.escalationReason());

        ServiceRecoveryCase saved = caseRepository.save(recoveryCase);

        recordAudit(
                "SERVICE_RECOVERY_ESCALATED",
                actor,
                "Case escalated to Director: " + recoveryCase.getCaseNumber() + " Reason: " + request.escalationReason(),
                "caseNumber=" + recoveryCase.getCaseNumber()
        );

        return toCaseDto(saved);
    }

    @Transactional
    public ServiceRecoveryCaseDto resolveCase(Long caseId, ResolveCaseRequest request, String actor) {
        log.info("Resolving service recovery caseId={}, actor={}", caseId, actor);
        ServiceRecoveryCase recoveryCase = findCaseOrThrow(caseId);

        if (recoveryCase.getStatus() == ServiceRecoveryCaseStatus.RESOLVED || recoveryCase.getStatus() == ServiceRecoveryCaseStatus.CLOSED) {
            throw new IllegalStateException("Case " + recoveryCase.getCaseNumber() + " is already in " + recoveryCase.getStatus() + " status");
        }

        recoveryCase.setStatus(ServiceRecoveryCaseStatus.RESOLVED);
        recoveryCase.setResolutionNotes(request.resolutionNotes());
        recoveryCase.setResolvedAt(Instant.now());

        ServiceRecoveryCase saved = caseRepository.save(recoveryCase);

        recordAudit(
                "SERVICE_RECOVERY_RESOLVED",
                actor,
                "Case resolved: " + recoveryCase.getCaseNumber(),
                "resolutionNotes=" + request.resolutionNotes()
        );

        return toCaseDto(saved);
    }

    @Transactional
    public ServiceRecoveryObcVoucherDto issueObcVoucher(Long caseId, IssueObcVoucherRequest request, String authorizedBy) {
        log.info("Issuing OBC compensation voucher for caseId={}, amount=${}, authorizedBy={}",
                caseId, request.creditAmount(), authorizedBy);
        ServiceRecoveryCase recoveryCase = caseRepository.findByIdWithLock(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Service recovery case not found with id: " + caseId));

        if (recoveryCase.getStatus() == ServiceRecoveryCaseStatus.CLOSED || recoveryCase.getStatus() == ServiceRecoveryCaseStatus.RESOLVED) {
            throw new IllegalStateException("Cannot issue voucher for case in status: " + recoveryCase.getStatus());
        }

        List<ServiceRecoveryObcVoucher> existingVouchers = voucherRepository.findByCaseId(recoveryCase.getId());
        if (existingVouchers != null && !existingVouchers.isEmpty()) {
            boolean activeExists = existingVouchers.stream()
                    .anyMatch(v -> v.getStatus() == ObcVoucherStatus.ISSUED || v.getStatus() == ObcVoucherStatus.CLAIMED_TO_FOLIO);
            if (activeExists) {
                throw new IllegalStateException("An active OBC voucher has already been issued for case " + recoveryCase.getCaseNumber());
            }
        }

        String voucherCode;
        do {
            voucherCode = "OBC-REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (voucherRepository.existsByVoucherCode(voucherCode));

        int validityHours = (request.validityHours() != null && request.validityHours() > 0) ? request.validityHours() : 72;
        Instant expiresAt = Instant.now().plus(validityHours, ChronoUnit.HOURS);

        ServiceRecoveryObcVoucher voucher = ServiceRecoveryObcVoucher.builder()
                .voucherCode(voucherCode)
                .caseId(recoveryCase.getId())
                .caseNumber(recoveryCase.getCaseNumber())
                .passengerId(recoveryCase.getPassengerId())
                .passengerName(recoveryCase.getPassengerName())
                .cabinNumber(recoveryCase.getCabinNumber())
                .creditAmount(request.creditAmount())
                .status(ObcVoucherStatus.ISSUED)
                .apologyTitle(request.apologyTitle())
                .apologyLetterBody(request.apologyLetterBody())
                .authorizedBy(authorizedBy)
                .issuedAt(Instant.now())
                .expiresAt(expiresAt)
                .build();

        ServiceRecoveryObcVoucher savedVoucher = voucherRepository.save(voucher);

        recoveryCase.setStatus(ServiceRecoveryCaseStatus.COMPENSATION_OFFERED);
        caseRepository.save(recoveryCase);

        recordAudit(
                "OBC_VOUCHER_ISSUED",
                authorizedBy,
                "Issued $" + request.creditAmount() + " OBC voucher " + voucherCode + " for case " + recoveryCase.getCaseNumber(),
                "cabin=" + recoveryCase.getCabinNumber() + ", code=" + voucherCode
        );

        return toVoucherDto(savedVoucher);
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public ClaimObcVoucherResponse claimObcVoucher(String voucherCode, String actor) {
        log.info("Claiming OBC voucherCode={} to folio, actor={}", voucherCode, actor);
        ServiceRecoveryObcVoucher voucher = voucherRepository.findByVoucherCodeWithLock(voucherCode)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found with code: " + voucherCode));

        if (voucher.getStatus() == ObcVoucherStatus.CLAIMED_TO_FOLIO) {
            throw new BusinessRuleException("Voucher " + voucherCode + " has already been claimed.");
        }
        if (voucher.getStatus() != ObcVoucherStatus.ISSUED) {
            throw new BusinessRuleException("Voucher is in invalid state for claim: " + voucher.getStatus());
        }
        if (Instant.now().isAfter(voucher.getExpiresAt())) {
            voucher.setStatus(ObcVoucherStatus.EXPIRED);
            voucherRepository.save(voucher);
            throw new BusinessRuleException("Voucher " + voucherCode + " has expired on " + voucher.getExpiresAt());
        }

        // Lock & credit folio account
        CabinFolioAccount folio = folioRepository.findByCabinNumberWithLock(voucher.getCabinNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Cabin folio not found: " + voucher.getCabinNumber()));

        if (folio.getStatus() != FolioStatus.ACTIVE) {
            throw new BusinessRuleException("Cannot credit compensation to inactive/settled folio: " + folio.getStatus());
        }

        // Apply credit (OBC raises available limit / decreases current balance)
        BigDecimal creditAmount = voucher.getCreditAmount();
        folio.credit(creditAmount);
        folioRepository.save(folio);

        String txId = "TX-OBC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Write ledger record
        GuestFolioLedger ledger = GuestFolioLedger.builder()
                .folioId("FOLIO-" + voucher.getCabinNumber())
                .cabinNumber(voucher.getCabinNumber())
                .passengerName(voucher.getPassengerName())
                .transactionId(txId)
                .description("Onboard Credit Compensation (Service Recovery: " + voucher.getCaseNumber() + ")")
                .serviceArea("GUEST_RELATIONS_RECOVERY")
                .amount(creditAmount)
                .isReversal(true) // acts as credit reduction to guest's folio dues
                .postedAt(Instant.now())
                .build();
        guestFolioLedgerRepository.save(ledger);

        // Update voucher status
        voucher.setStatus(ObcVoucherStatus.CLAIMED_TO_FOLIO);
        voucher.setClaimedAt(Instant.now());
        voucher.setFolioTransactionId(txId);
        ServiceRecoveryObcVoucher updatedVoucher = voucherRepository.save(voucher);

        recordAudit(
                "OBC_VOUCHER_CLAIMED",
                actor != null ? actor : voucher.getPassengerName(),
                "Claimed $" + creditAmount + " OBC to cabin " + voucher.getCabinNumber() + " (tx=" + txId + ")",
                "voucherCode=" + voucherCode
        );

        return new ClaimObcVoucherResponse(
                toVoucherDto(updatedVoucher),
                txId,
                creditAmount,
                updatedVoucher.getCabinNumber(),
                folio.getAvailableLimit(),
                updatedVoucher.getClaimedAt(),
                "$" + creditAmount + " Onboard Credit successfully credited to cabin folio " + updatedVoucher.getCabinNumber()
        );
    }

    @Transactional(readOnly = true)
    public List<ServiceRecoveryObcVoucherDto> getVouchersForCase(Long caseId) {
        return voucherRepository.findByCaseId(caseId).stream()
                .map(this::toVoucherDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ServiceRecoveryObcVoucherDto> getPassengerVouchers(Long passengerId) {
        return voucherRepository.findByPassengerIdOrderByCreatedAtDesc(passengerId).stream()
                .map(this::toVoucherDto)
                .collect(Collectors.toList());
    }

    private ServiceRecoveryCase findCaseOrThrow(Long caseId) {
        return caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Service recovery case not found with id: " + caseId));
    }

    private Instant computeSlaDueDate(ServiceRecoverySeverity severity, Instant from) {
        return switch (severity) {
            case CRITICAL_DIRECTOR_ESCALATION -> from.plus(2, ChronoUnit.HOURS);
            case HIGH -> from.plus(6, ChronoUnit.HOURS);
            case MEDIUM -> from.plus(12, ChronoUnit.HOURS);
            case LOW -> from.plus(24, ChronoUnit.HOURS);
        };
    }

    private void recordAudit(String action, String actor, String reason, String details) {
        try {
            PosAuditLog auditLog = PosAuditLog.builder()
                    .action(action)
                    .performedBy(actor)
                    .reason(reason)
                    .details(details)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            log.error("Failed to record service recovery audit log: {}", ex.getMessage(), ex);
        }
    }

    private ServiceRecoveryCaseDto toCaseDto(ServiceRecoveryCase c) {
        return new ServiceRecoveryCaseDto(
                c.getId(),
                c.getCaseNumber(),
                c.getPassengerId(),
                c.getPassengerName(),
                c.getCabinNumber(),
                c.getRootCause(),
                c.getSeverity(),
                c.getStatus(),
                c.getComplaintSummary(),
                c.getComplaintDetails(),
                c.getAssignedHandler(),
                c.getSlaDueAt(),
                c.getEscalatedAt(),
                c.getEscalationReason(),
                c.getResolutionNotes(),
                c.getResolvedAt(),
                c.getCreatedAt() != null ? c.getCreatedAt() : Instant.now()
        );
    }

    private ServiceRecoveryObcVoucherDto toVoucherDto(ServiceRecoveryObcVoucher v) {
        return new ServiceRecoveryObcVoucherDto(
                v.getId(),
                v.getVoucherCode(),
                v.getCaseId(),
                v.getCaseNumber(),
                v.getPassengerId(),
                v.getPassengerName(),
                v.getCabinNumber(),
                v.getCreditAmount(),
                v.getStatus(),
                v.getApologyTitle(),
                v.getApologyLetterBody(),
                v.getAuthorizedBy(),
                v.getIssuedAt(),
                v.getExpiresAt(),
                v.getClaimedAt(),
                v.getFolioTransactionId(),
                v.getCreatedAt() != null ? v.getCreatedAt() : Instant.now()
        );
    }
}
