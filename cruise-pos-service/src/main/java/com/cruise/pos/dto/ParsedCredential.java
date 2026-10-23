package com.cruise.pos.dto;

import com.cruise.pos.enums.CredentialType;
import lombok.Value;

@Value
public class ParsedCredential {
    CredentialType type;
    String identifier;
}
