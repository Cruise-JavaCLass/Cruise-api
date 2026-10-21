package com.cruise.activity.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbarkationCheckInRequest {

    @Size(max = 100, message = "Staff ID tối đa 100 ký tự")
    private String staffId;

    @Size(max = 100, message = "Device ID tối đa 100 ký tự")
    private String terminalDeviceId;

    @Size(max = 50, message = "RFID tag tối đa 50 ký tự")
    private String rfidTag;

    @Size(max = 50, message = "Gate lane tối đa 50 ký tự")
    private String gateLane;

    @Size(max = 50, message = "Phương thức xác thực tối đa 50 ký tự")
    private String verificationMethod;
}
