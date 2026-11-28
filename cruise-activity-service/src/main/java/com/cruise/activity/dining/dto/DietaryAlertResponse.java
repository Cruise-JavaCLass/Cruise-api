package com.cruise.activity.dining.dto;

import com.cruise.activity.dining.enums.AllergenType;
import com.cruise.activity.dining.enums.SeverityLevel;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DietaryAlertResponse {
    private Long passengerId;
    private AllergenType allergenType;
    private SeverityLevel severityLevel;
    private String specialNotes;
}