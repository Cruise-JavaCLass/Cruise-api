package com.cruise.pos.entity;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name="pos_sync_conflicts", indexes=@Index(name="idx_sync_conflict_status",columnList="resolution_status, batch_id")) @Getter @Setter @NoArgsConstructor
public class SyncConflictLog extends BaseEntity {
 @Column(name="batch_id",nullable=false) private Long batchId;
 @Column(nullable=false,unique=true,length=36) private String idempotencyKey;
 @Column(nullable=false,length=128) private String transactionReference;
 @Column(nullable=false,length=64) private String terminalId;
 @Column(nullable=false,length=64) private String conflictType;
 @Column(nullable=false,columnDefinition="TEXT") private String serverStateJson;
 @Column(nullable=false,columnDefinition="TEXT") private String clientStateJson;
 @Column(name="resolution_status",nullable=false,length=16) private String resolutionStatus;
 @Column(length=100) private String resolvedBy;
 private java.time.Instant resolvedAt;
 @Column(length=512) private String resolutionReason;
}
