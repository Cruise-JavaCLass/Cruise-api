package com.cruise.pos.dto;

import com.cruise.pos.enums.FolioStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFolioStatusRequest {

    @NotNull(message = "Trạng thái tài khoản không được để trống")
    private FolioStatus status;

    private String reason;
}
