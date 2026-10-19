package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.CreateDisputeRequest;
import com.cruise.pos.dto.DisputeCaseDto;
import com.cruise.pos.dto.ResolveDisputeRequest;
import com.cruise.pos.enums.DisputeStatus;
import com.cruise.pos.service.DisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pos/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    @PostMapping
    public ResponseEntity<ApiResponse<DisputeCaseDto>> createDispute(@Valid @RequestBody CreateDisputeRequest request) {
        DisputeCaseDto created = disputeService.createDispute(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo yêu cầu khiếu nại chi phí thành công", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DisputeCaseDto>>> getDisputes(
            @RequestParam(required = false) DisputeStatus status,
            @RequestParam(required = false) String cabinNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<DisputeCaseDto> disputes = disputeService.getDisputes(
                status, cabinNumber, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.ok(disputes));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DisputeCaseDto>> getDisputeById(@PathVariable Long id) {
        DisputeCaseDto dispute = disputeService.getDisputeById(id);
        return ResponseEntity.ok(ApiResponse.ok(dispute));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<DisputeCaseDto>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody com.cruise.pos.dto.UpdateDisputeStatusRequest request) {
        String actorId = request.getActorId() != null && !request.getActorId().isBlank() ? request.getActorId() : "STAFF";
        String note = request.getNote() != null && !request.getNote().isBlank() ? request.getNote() : "Cập nhật tiến độ điều tra";
        DisputeCaseDto updated = disputeService.updateStatus(id, request.getStatus(), actorId, note);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái khiếu nại thành công", updated));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<DisputeCaseDto>> resolveDispute(
            @PathVariable Long id,
            @Valid @RequestBody ResolveDisputeRequest request) {
        DisputeCaseDto resolved = disputeService.resolveDispute(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Quyết định giải quyết khiếu nại thành công", resolved));
    }
}
