package com.cruise.pos.entity;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name="pos_sync_transaction_records", uniqueConstraints=@UniqueConstraint(name="uk_sync_batch_position",columnNames={"batch_id","record_position"})) @Getter @Setter @NoArgsConstructor
public class SyncTransactionRecord extends BaseEntity {
 @Column(length=32) private String cabinNumber;
 @Column(name="batch_id",nullable=false) private Long batchId;
 @Column(name="record_position",nullable=false) private Integer recordPosition;
 @Column(nullable=false,length=36) private String idempotencyKey;
 @Column(nullable=false,length=128) private String transactionReference;
 @Column(nullable=false,length=16) private String resultStatus;
 private Long serverTransactionId;
 private Long conflictId;
 @Column(length=128) private String errorCode;
}
