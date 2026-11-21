package com.cruise.pos.entity;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name="pos_sync_idempotency") @Getter @Setter @NoArgsConstructor
public class IdempotencyRecord extends BaseEntity {
 @Column(nullable=false,unique=true,length=36) private String idempotencyKey;
 @Column(nullable=false,unique=true,length=128) private String transactionReference;
 @Column(nullable=false,length=64) private String terminalId;
 @Column(nullable=false,length=128) private String endpoint;
 @Column(nullable=false,length=64) private String requestHash;
 @Column(columnDefinition="TEXT") private String responseBody;
 @Column(nullable=false,length=16) private String status;
 @Column(nullable=false) private java.time.Instant expiresAt;
}
