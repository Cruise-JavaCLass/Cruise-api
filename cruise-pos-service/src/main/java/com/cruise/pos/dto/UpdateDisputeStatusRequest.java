package com.cruise.pos.dto;

import com.cruise.pos.enums.DisputeStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDisputeStatusRequest {

    @NotNull(message = "Trạng thái mới không được để trống")
    private DisputeStatus status;

    @Size(max = 64, message = "Mã nhân sự xử lý tối đa 64 ký tự")
    private String actorId;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
