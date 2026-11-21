package com.cruise.pos.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cruise.pos.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

@Component @RequiredArgsConstructor
public class OfflineSyncCodec {
    private final ObjectMapper mapper;
    public String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Unable to serialize sync state",error); }
    }
    public <T> T read(String value,Class<T> type) {
        try { return mapper.readValue(value,type); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Unable to read persisted sync state",error); }
    }
    public String hash(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json(value).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    public OfflineTransactionDto normalize(OfflineTransactionDto value) {
        if (value==null || value.idempotencyKey()==null || value.idempotencyKey().version()!=7 || value.idempotencyKey().variant()!=2
                || value.transaction()==null || value.transactionReference()==null || value.transactionReference().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Each offline transaction requires a UUIDv7 key and reference");
        }
        CreateTransactionRequest request=read(json(value.transaction()),CreateTransactionRequest.class);
        if (request.getTransactionUuid()!=null && !value.transactionReference().equals(request.getTransactionUuid())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Transaction reference does not match transactionUuid");
        }
        request.setTransactionUuid(value.transactionReference());
        return new OfflineTransactionDto(value.idempotencyKey(),value.transactionReference(),request);
    }
}
