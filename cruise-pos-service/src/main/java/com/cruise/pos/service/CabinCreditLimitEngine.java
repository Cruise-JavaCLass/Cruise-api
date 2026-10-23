package com.cruise.pos.service;

import com.cruise.pos.dto.CabinCreditLimitCheckRequest;
import com.cruise.pos.dto.CabinCreditLimitCheckResponse;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.entity.CabinMember;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.CabinMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CabinCreditLimitEngine {

    private final CabinFolioAccountRepository folioRepository;
    private final CabinMemberRepository memberRepository;
    private final CabinBillingSplitService billingSplitService;

    @Transactional(readOnly = true)
    public CabinCreditLimitCheckResponse checkCreditLimit(String cabinNumber, CabinCreditLimitCheckRequest request) {
        CabinFolioAccount folio = folioRepository.findByCabinNumber(cabinNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản Folio cho cabin: " + cabinNumber));

        BigDecimal requested = request.getAmount();
        BigDecimal cabinLimit = folio.getCreditLimit();
        BigDecimal cabinBalance = folio.getCurrentBalance();
        BigDecimal cabinAvailable = folio.getAvailableLimit();

        // 1. Cabin Status Lock Check
        if (folio.getStatus() == FolioStatus.LOCKED) {
            return buildLockedResponse(cabinNumber, request.getPassengerId(), requested, folio,
                    "Tài khoản phòng đang bị KHÓA (Folio Locked)!");
        }
        if (folio.getStatus() != FolioStatus.ACTIVE) {
            return buildLockedResponse(cabinNumber, request.getPassengerId(), requested, folio,
                    "Tài khoản phòng không ở trạng thái ACTIVE (Status: " + folio.getStatus() + ")!");
        }

        // 2. Cabin Over-limit Check
        if (cabinAvailable.compareTo(requested) < 0) {
            return CabinCreditLimitCheckResponse.builder()
                    .cabinNumber(cabinNumber)
                    .passengerId(request.getPassengerId())
                    .isAllowed(false)
                    .isLocked(true)
                    .isOverLimit(true)
                    .lockReason(String.format("Vượt hạn mức tín dụng phòng! Khả dụng: $%.2f, Yêu cầu: $%.2f (Cabin Over-limit)",
                            cabinAvailable, requested))
                    .requestedAmount(requested)
                    .cabinCreditLimit(cabinLimit)
                    .cabinCurrentBalance(cabinBalance)
                    .cabinAvailableLimit(cabinAvailable)
                    .build();
        }

        // 3. Member Signer & Individual Limit Check
        billingSplitService.ensureSeededCabinMembers(cabinNumber);
        if (request.getPassengerId() != null) {
            Optional<CabinMember> memberOpt = memberRepository.findByCabinNumberAndPassengerId(cabinNumber, request.getPassengerId());
            if (memberOpt.isEmpty()) {
                return buildLockedResponse(cabinNumber, request.getPassengerId(), requested, folio,
                        "Hành khách không thuộc phòng " + cabinNumber + "!");
            }
            if (memberOpt.isPresent()) {
                CabinMember member = memberOpt.get();

                if (Boolean.TRUE.equals(member.getIsMinor()) || !Boolean.TRUE.equals(member.getIsAuthorizedSigner())) {
                    return CabinCreditLimitCheckResponse.builder()
                            .cabinNumber(cabinNumber)
                            .passengerId(member.getPassengerId())
                            .isAllowed(false)
                            .isLocked(true)
                            .isOverLimit(false)
                            .lockReason(String.format("Hành khách %s không có quyền ký thanh toán (Minor/Unauthorized Signer Lock)", member.getGuestName()))
                            .requestedAmount(requested)
                            .cabinCreditLimit(cabinLimit)
                            .cabinCurrentBalance(cabinBalance)
                            .cabinAvailableLimit(cabinAvailable)
                            .build();
                }

                if (member.getIndividualCreditLimit() != null) {
                    BigDecimal memberLimit = member.getIndividualCreditLimit();
                    BigDecimal memberBalance = member.getIndividualBalance() != null ? member.getIndividualBalance() : BigDecimal.ZERO;
                    BigDecimal memberAvail = memberLimit.subtract(memberBalance);

                    if (memberAvail.compareTo(requested) < 0) {
                        return CabinCreditLimitCheckResponse.builder()
                                .cabinNumber(cabinNumber)
                                .passengerId(member.getPassengerId())
                                .isAllowed(false)
                                .isLocked(true)
                                .isOverLimit(true)
                                .lockReason(String.format("Vượt hạn mức cá nhân của khách %s! Khả dụng: $%.2f, Yêu cầu: $%.2f (Member Over-limit)",
                                        member.getGuestName(), memberAvail, requested))
                                .requestedAmount(requested)
                                .cabinCreditLimit(cabinLimit)
                                .cabinCurrentBalance(cabinBalance)
                                .cabinAvailableLimit(cabinAvailable)
                                .memberCreditLimit(memberLimit)
                                .memberCurrentBalance(memberBalance)
                                .memberAvailableLimit(memberAvail)
                                .build();
                    }

                    return CabinCreditLimitCheckResponse.builder()
                            .cabinNumber(cabinNumber)
                            .passengerId(member.getPassengerId())
                            .isAllowed(true)
                            .isLocked(false)
                            .isOverLimit(false)
                            .requestedAmount(requested)
                            .cabinCreditLimit(cabinLimit)
                            .cabinCurrentBalance(cabinBalance)
                            .cabinAvailableLimit(cabinAvailable)
                            .memberCreditLimit(memberLimit)
                            .memberCurrentBalance(memberBalance)
                            .memberAvailableLimit(memberAvail)
                            .build();
                }
            }
        }

        // Allowed
        return CabinCreditLimitCheckResponse.builder()
                .cabinNumber(cabinNumber)
                .passengerId(request.getPassengerId())
                .isAllowed(true)
                .isLocked(false)
                .isOverLimit(false)
                .requestedAmount(requested)
                .cabinCreditLimit(cabinLimit)
                .cabinCurrentBalance(cabinBalance)
                .cabinAvailableLimit(cabinAvailable)
                .build();
    }

    private CabinCreditLimitCheckResponse buildLockedResponse(String cabin, Long pid, BigDecimal req, CabinFolioAccount f, String reason) {
        return CabinCreditLimitCheckResponse.builder()
                .cabinNumber(cabin)
                .passengerId(pid)
                .isAllowed(false)
                .isLocked(true)
                .isOverLimit(false)
                .lockReason(reason)
                .requestedAmount(req)
                .cabinCreditLimit(f.getCreditLimit())
                .cabinCurrentBalance(f.getCurrentBalance())
                .cabinAvailableLimit(f.getAvailableLimit())
                .build();
    }
}
