package com.cruise.pos.dto;

import com.cruise.pos.enums.VoyageLocationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartPriceCalculationRequest {

    private Long outletId;

    @Builder.Default
    private VoyageLocationStatus voyageLocation = VoyageLocationStatus.IN_PORT;

    private String portName;

    // Optional client time in format "HH:mm" to simulate or test Happy Hour
    private String simulatedTime;

    @NotEmpty(message = "Danh sách món trong giỏ hàng không được rỗng")
    @Valid
    @Builder.Default
    private List<CartItemCalculationRequest> items = new ArrayList<>();
}
