package com.cruise.activity.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionRosterSummaryDto {

    private Long excursionId;
    private String excursionTitle;
    private Integer maxCapacity;
    private Long confirmedCount;
    private Long waitlistCount;
    private Long cancelledCount;
    private Long checkedInCount;
    private Long specialAssistanceCount;
    private List<ExcursionPassengerRosterDto> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
