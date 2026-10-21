package com.cruise.activity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReassignBusRequest {

    @NotBlank(message = "newBusNumber không được để trống")
    private String newBusNumber;

    private String newGroupName;
}
