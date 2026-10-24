package com.cruise.pos.dto;

import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.ReportDimension;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationalReportQuery {

    private Instant startDate;
    private Instant endDate;
    private Long outletId;
    private String category;
    private PaymentMethod paymentMethod;
    private ReportDimension dimension;
}
