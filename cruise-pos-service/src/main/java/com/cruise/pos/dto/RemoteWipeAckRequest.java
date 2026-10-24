package com.cruise.pos.dto;

public record RemoteWipeAckRequest(
        String wipeStatus,
        String executionDetails
) {}
