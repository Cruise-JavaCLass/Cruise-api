package com.cruise.pos.entity;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name="pos_sync_batches", uniqueConstraints=@UniqueConstraint(name="uk_sync_terminal_batch",columnNames={"terminal_id","batch_number"})) @Getter @Setter @NoArgsConstructor
public class SyncBatch extends BaseEntity {
 @Column(name="terminal_id",nullable=false,length=64) private String terminalId;
 @Column(name="batch_number",nullable=false,length=128) private String batchNumber;
 @Column(nullable=false,length=64) private String requestHash;
 @Column(nullable=false) private int totalRecords;
 @Column(nullable=false) private int successfulCount;
 @Column(nullable=false) private int failedCount;
 @Column(nullable=false) private int duplicateCount;
 @Column(nullable=false,length=32) private String syncStatus;
 @Column(columnDefinition="TEXT") private String responseBody;
}
