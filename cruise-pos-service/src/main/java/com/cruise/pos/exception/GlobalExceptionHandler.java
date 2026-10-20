package com.cruise.pos.exception;

import com.cruise.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(ResourceNotFoundException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusinessRule(BusinessRuleException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleIdempotencyConflict(IdempotencyConflictException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(InsufficientFolioBalanceException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiResponse<Void> handleInsufficientBalance(InsufficientFolioBalanceException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedManagerOverrideException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleUnauthorizedManager(UnauthorizedManagerOverrideException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(FolioAlreadySettledException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleFolioAlreadySettled(FolioAlreadySettledException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(FolioNotActiveException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleFolioNotActive(FolioNotActiveException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(OpenDisputesPendingException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleOpenDisputes(OpenDisputesPendingException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(InvoiceNotAvailableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleInvoiceNotAvailable(InvoiceNotAvailableException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(InsufficientTenderException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiResponse<Void> handleInsufficientTender(InsufficientTenderException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyRequiredException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIdempotencyKeyRequired(IdempotencyKeyRequiredException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Dữ liệu đầu vào không hợp lệ");
        return ApiResponse.error(msg);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadRequest(Exception ex) {
        log.warn("Invalid client request format: {}", ex.getMessage());
        return ApiResponse.error("Dữ liệu đầu vào không hợp lệ");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleGeneral(Exception ex) {
        log.error("Unhandled server exception: ", ex);
        return ApiResponse.error("Đã xảy ra lỗi máy chủ nội bộ");
    }
}
