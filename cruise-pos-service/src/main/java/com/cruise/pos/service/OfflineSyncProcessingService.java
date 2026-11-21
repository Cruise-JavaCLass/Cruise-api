package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.dto.OfflineSyncDtos.*;
import com.cruise.pos.entity.*;
import com.cruise.pos.enums.*;
import com.cruise.pos.repository.*;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
public class OfflineSyncProcessingService {
    private static final String ENDPOINT="/api/v1/pos/sync/batch";
    private final SyncBatchRepository batches;
    private final IdempotencyRecordRepository keys;
    private final SyncTransactionRecordRepository records;
    private final SyncConflictLogRepository conflicts;
    private final PosTerminalRepository terminals;
    private final CabinFolioAccountRepository folios;
    private final PosIdempotencyRepository onlineKeys;
    private final PosTransactionRepository transactions;
    private final PosShiftRepository shifts;
    private final PosShiftService shiftService;
    private final PosTransactionService transactionService;
    private final PosAuditLogRepository audit;
    private final OfflineSyncCodec codec;
    private final Validator validator;
    private final Clock clock;

    @Transactional
    public BatchResult processBatch(String terminalId,String batchNumber,List<OfflineTransactionDto> input) {
        if (batchNumber==null || batchNumber.isBlank() || batchNumber.length()>128 || input==null || input.isEmpty() || input.size()>250) {
            throw badRequest("Batch ID and 1 to 250 transactions are required");
        }
        List<OfflineTransactionDto> items=input.stream().map(codec::normalize).toList();
        for (OfflineTransactionDto item:items) if (!validator.validate(item).isEmpty()) throw badRequest("Invalid offline transaction fields");
        PosTerminal terminal=requireTerminal(terminalId);
        for (OfflineTransactionDto item:items) if (!terminal.getStationId().equals(item.transaction().getStationId())) throw badRequest("Transaction station does not match terminal");
        String requestHash=codec.hash(items);
        Optional<SyncBatch> replay=batches.findByTerminalIdAndBatchNumber(terminalId,batchNumber);
        if (replay.isPresent()) {
            if (!requestHash.equals(replay.get().getRequestHash())) throw conflict("Batch ID was already used for a different payload");
            return result(replay.get(),true);
        }
        SyncBatch batch=new SyncBatch(); batch.setTerminalId(terminalId); batch.setBatchNumber(batchNumber);
        batch.setRequestHash(requestHash); batch.setTotalRecords(items.size()); batch.setSyncStatus("SUBMITTED"); batches.saveAndFlush(batch);
        batch.setSyncStatus("PROCESSING");
        // All existing POS writes acquire shift before folio: preserve that order to avoid online/offline deadlocks.
        Map<Long,PosShift> lockedShifts=new HashMap<>();
        items.stream().map(item->item.transaction().getShiftId()).filter(Objects::nonNull).distinct().sorted()
            .forEach(id->shifts.findByIdWithLock(id).ifPresent(shift->lockedShifts.put(id,shift)));
        PosShift defaultShift=items.stream().anyMatch(item->item.transaction().getShiftId()==null)
            ? shiftService.openShift(terminal.getStationId(),items.getFirst().transaction().getCashierId(),BigDecimal.ZERO) : null;
        Map<String,CabinFolioAccount> lockedFolios=new HashMap<>();
        items.stream().map(item->item.transaction().getCabinNumber()).filter(Objects::nonNull).distinct().sorted()
            .forEach(cabin->folios.findByCabinNumberWithLock(cabin).ifPresent(folio->lockedFolios.put(cabin,folio)));
        for (int position=0;position<items.size();position++) {
            OfflineTransactionDto item=items.get(position);
            RecordResult response=processItem(batch,item,lockedShifts,defaultShift,lockedFolios);
            SyncTransactionRecord record=new SyncTransactionRecord(); record.setBatchId(batch.getId()); record.setRecordPosition(position);
            record.setIdempotencyKey(response.idempotencyKey()); record.setTransactionReference(response.transactionReference());
            record.setCabinNumber(item.transaction().getCabinNumber()); apply(record,response); records.save(record);
        }
        terminal.setLastSeenAt(clock.instant()); terminals.save(terminal);
        records.flush(); updateBatchCounters(batch);
        return result(batch,false);
    }

    private RecordResult processItem(SyncBatch batch,OfflineTransactionDto item,Map<Long,PosShift> lockedShifts,
            PosShift defaultShift,Map<String,CabinFolioAccount> lockedFolios) {
        String key=item.idempotencyKey().toString();
        String hash=codec.hash(List.of(ENDPOINT,batch.getTerminalId(),item.transactionReference(),item.transaction()));
        Optional<IdempotencyRecord> previous=keys.findByIdempotencyKey(key);
        if (previous.isEmpty()) previous=keys.findByTransactionReference(item.transactionReference());
        if (previous.isPresent()) {
            IdempotencyRecord existing=previous.get();
            if (!key.equals(existing.getIdempotencyKey()) || !hash.equals(existing.getRequestHash()) || !batch.getTerminalId().equals(existing.getTerminalId())) throw conflict("Idempotency key/reference was used for a different payload");
            if (!"COMPLETED".equals(existing.getStatus())) throw conflict("Idempotency key is being processed");
            RecordResult cached=codec.read(existing.getResponseBody(),RecordResult.class);
            return new RecordResult(key,item.transactionReference(),"SUCCESS".equals(cached.status())?"DUPLICATE":cached.status(),cached.serverTransactionId(),cached.conflictId(),cached.errorCode());
        }
        if (onlineKeys.findByIdempotencyKey(key).isPresent() || transactions.findByTransactionUuid(item.transactionReference()).isPresent()) {
            throw conflict("Transaction already exists outside this sync endpoint; manual reconciliation required");
        }
        IdempotencyRecord claim=new IdempotencyRecord(); claim.setIdempotencyKey(key); claim.setTransactionReference(item.transactionReference());
        claim.setTerminalId(batch.getTerminalId()); claim.setEndpoint(ENDPOINT); claim.setRequestHash(hash); claim.setStatus("LOCKED");
        claim.setExpiresAt(clock.instant().plus(Duration.ofDays(30))); keys.saveAndFlush(claim);
        CreateTransactionRequest request=item.transaction(); CabinFolioAccount folio=lockedFolios.get(request.getCabinNumber());
        RecordResult response;
        if (request.getPaymentMethod()==PaymentMethod.CABIN_FOLIO && folio==null) response=error(item,"FOLIO_NOT_FOUND",null);
        else if (request.getPaymentMethod()==PaymentMethod.CABIN_FOLIO && (folio.getStatus()==FolioStatus.SETTLED || folio.getStatus()==FolioStatus.CLOSED)) {
            SyncConflictLog log=new SyncConflictLog(); log.setBatchId(batch.getId()); log.setTerminalId(batch.getTerminalId());
            log.setIdempotencyKey(key); log.setTransactionReference(item.transactionReference()); log.setConflictType("FOLIO_CLOSED");
            log.setServerStateJson(codec.json(folioState(folio))); log.setClientStateJson(codec.json(item)); log.setResolutionStatus("PENDING");
            conflicts.saveAndFlush(log); response=error(item,"FOLIO_CLOSED",log.getId());
        } else {
            PosShift shift=request.getShiftId()==null?defaultShift:lockedShifts.get(request.getShiftId());
            String issue=preflight(request,folio,shift);
            if (issue!=null) response=error(item,issue,null);
            else {
                request.setShiftId(shift.getId());
                TransactionResponse applied=transactionService.processChargeTransaction(key,request);
                response=new RecordResult(key,item.transactionReference(),"SUCCESS",applied.getId(),null,null);
            }
        }
        claim.setResponseBody(codec.json(response)); claim.setStatus("COMPLETED"); keys.save(claim);
        return response;
    }

    @Transactional(readOnly=true)
    public BatchResult getStatus(Long batchId,String terminalId) {
        SyncBatch batch=batches.findById(batchId).orElseThrow(()->notFound("Unknown sync batch"));
        if (!batch.getTerminalId().equals(terminalId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Batch belongs to another terminal");
        return result(batch,false);
    }
    @Transactional(readOnly=true)
    public Page<SyncConflictLog> getConflicts(String status,int page,int size) {
        if (!Set.of("PENDING","APPROVED","REJECTED").contains(status) || page<0 || size<1 || size>100) throw badRequest("Invalid conflict filter/page");
        return conflicts.findByResolutionStatus(status,PageRequest.of(page,size,Sort.by("id")));
    }
    @Transactional
    public RecordResult resolve(Long conflictId,ResolveRequest decision,String actor) {
        SyncConflictLog log=conflicts.findByIdForUpdate(conflictId).orElseThrow(()->notFound("Unknown sync conflict"));
        IdempotencyRecord claim=keys.findByIdempotencyKey(log.getIdempotencyKey()).orElseThrow(()->notFound("Missing idempotency claim"));
        if (!"PENDING".equals(log.getResolutionStatus())) return codec.read(claim.getResponseBody(),RecordResult.class);
        OfflineTransactionDto item=codec.read(log.getClientStateJson(),OfflineTransactionDto.class);
        RecordResult outcome;
        if (decision.action()==ResolutionAction.REJECT) outcome=error(item,"MANUAL_REJECTION",log.getId());
        else {
            PosTerminal terminal=terminals.findByDeviceId(log.getTerminalId()).orElseThrow(()->notFound("Unknown terminal"));
            PosShift shift=shiftService.openShift(terminal.getStationId(),actor,BigDecimal.ZERO);
            CabinFolioAccount folio=folios.findByCabinNumberWithLock(item.transaction().getCabinNumber()).orElseThrow(()->notFound("Missing cabin folio"));
            FolioStatus original=folio.getStatus();
            if (original!=FolioStatus.SETTLED && original!=FolioStatus.CLOSED && original!=FolioStatus.ACTIVE) throw conflict("Locked/frozen folio cannot be overridden by sync resolution");
            folio.setStatus(FolioStatus.ACTIVE);
            try {
                String issue=preflight(item.transaction(),folio,shift);
                if (issue!=null) throw conflict(issue);
                item.transaction().setShiftId(shift.getId());
                TransactionResponse applied=transactionService.processChargeTransaction(log.getIdempotencyKey(),item.transaction());
                outcome=new RecordResult(log.getIdempotencyKey(),log.getTransactionReference(),"SUCCESS",applied.getId(),log.getId(),null);
            } finally { folio.setStatus(original); folios.save(folio); }
        }
        log.setResolutionStatus(decision.action()==ResolutionAction.APPROVE?"APPROVED":"REJECTED");
        log.setResolvedBy(actor); log.setResolvedAt(clock.instant()); log.setResolutionReason(decision.reason()); conflicts.saveAndFlush(log);
        claim.setResponseBody(codec.json(outcome)); keys.save(claim);
        Set<Long> affected=new HashSet<>();
        for (SyncTransactionRecord record:records.findByIdempotencyKey(log.getIdempotencyKey())) {
            apply(record,outcome); if (!record.getBatchId().equals(log.getBatchId()) && "SUCCESS".equals(outcome.status())) record.setResultStatus("DUPLICATE");
            records.save(record); affected.add(record.getBatchId());
        }
        records.flush(); affected.forEach(id->batches.findById(id).ifPresent(this::updateBatchCounters));
        audit.save(PosAuditLog.builder().action("OFFLINE_SYNC_"+decision.action()).performedBy(actor)
            .transactionId(outcome.serverTransactionId()).reason(decision.reason()).details("Conflict="+conflictId+", reference="+log.getTransactionReference()).build());
        return outcome;
    }
    @Transactional
    public HeartbeatResult heartbeat(String terminalId,HeartbeatRequest request) {
        PosTerminal terminal=requireTerminal(terminalId); terminal.setLastSeenAt(clock.instant());
        if (request.firmwareVersion()!=null) terminal.setFirmwareVersion(request.firmwareVersion());
        terminals.save(terminal); return new HeartbeatResult(terminalId,terminal.getStationId(),terminal.getLastSeenAt());
    }
    private PosTerminal requireTerminal(String id) {
        if (id==null || id.isBlank() || id.length()>64) throw badRequest("Terminal ID required");
        PosTerminal terminal=terminals.findByDeviceIdForUpdate(id).orElseThrow(()->notFound("Unknown registered terminal"));
        if (terminal.getStatus()!=PosTerminalStatus.ACTIVE) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Terminal is not active");
        return terminal;
    }
    private String preflight(CreateTransactionRequest request,CabinFolioAccount folio,PosShift shift) {
        if (shift==null || shift.getStatus()!=ShiftStatus.OPEN || !request.getStationId().equals(shift.getStationId())) return "SHIFT_NOT_OPEN";
        BigDecimal total=total(request);
        if (total.scale()>2 || total.compareTo(new BigDecimal("9999999999.99"))>0) return "INVALID_AMOUNT";
        if (request.getItems().stream().anyMatch(item->item.getUnitPrice().stripTrailingZeros().scale()>2)) return "INVALID_UNIT_PRICE";
        if (request.getPaymentMethod()==PaymentMethod.CABIN_FOLIO) {
            if (folio==null) return "FOLIO_NOT_FOUND";
            if (folio.getStatus()!=FolioStatus.ACTIVE) return "FOLIO_NOT_ACTIVE";
            if (!folio.canDebit(total)) return "INSUFFICIENT_CREDIT";
        }
        return null;
    }
    private static BigDecimal total(CreateTransactionRequest request) {
        return request.getItems().stream().map(item->item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO,BigDecimal::add).add(zero(request.getTax())).add(zero(request.getGratuity()))
            .subtract(zero(request.getDiscount())).max(BigDecimal.ZERO).stripTrailingZeros();
    }
    private static BigDecimal zero(BigDecimal value) { return value==null?BigDecimal.ZERO:value; }
    private RecordResult error(OfflineTransactionDto item,String code,Long conflict) { return new RecordResult(item.idempotencyKey().toString(),item.transactionReference(),"ERROR",null,conflict,code); }
    private static void apply(SyncTransactionRecord record,RecordResult result) { record.setResultStatus(result.status()); record.setServerTransactionId(result.serverTransactionId()); record.setConflictId(result.conflictId()); record.setErrorCode(result.errorCode()); }
    private void updateBatchCounters(SyncBatch batch) {
        List<SyncTransactionRecord> items=records.findByBatchIdOrderByRecordPositionAsc(batch.getId());
        batch.setSuccessfulCount((int)items.stream().filter(item->"SUCCESS".equals(item.getResultStatus())).count());
        batch.setDuplicateCount((int)items.stream().filter(item->"DUPLICATE".equals(item.getResultStatus())).count());
        batch.setFailedCount(items.size()-batch.getSuccessfulCount()-batch.getDuplicateCount());
        batch.setSyncStatus(conflicts.countByBatchIdAndResolutionStatus(batch.getId(),"PENDING")>0?"CONFLICT_DETECTED":"COMPLETED"); batches.save(batch);
    }
    private BatchResult result(SyncBatch batch,boolean replay) {
        List<SyncTransactionRecord> entries=records.findByBatchIdOrderByRecordPositionAsc(batch.getId());
        List<RecordResult> responses=entries.stream().map(item->new RecordResult(item.getIdempotencyKey(),item.getTransactionReference(),
            replay && "SUCCESS".equals(item.getResultStatus())?"DUPLICATE":item.getResultStatus(),item.getServerTransactionId(),item.getConflictId(),item.getErrorCode())).toList();
        List<FolioState> authoritative=entries.stream().map(SyncTransactionRecord::getCabinNumber).filter(Objects::nonNull).distinct().sorted()
            .map(folios::findByCabinNumber).flatMap(Optional::stream).map(OfflineSyncProcessingService::folioState).toList();
        int success=(int)responses.stream().filter(item->"SUCCESS".equals(item.status())).count();
        int duplicates=(int)responses.stream().filter(item->"DUPLICATE".equals(item.status())).count();
        return new BatchResult(batch.getId(),batch.getTerminalId(),batch.getBatchNumber(),batch.getSyncStatus(),entries.size(),success,duplicates,
            entries.size()-success-duplicates,responses,authoritative,clock.instant());
    }
    private static FolioState folioState(CabinFolioAccount folio) { return new FolioState(folio.getCabinNumber(),folio.getStatus().name(),folio.getCurrentBalance(),folio.getCreditLimit()); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND,message); }
}
