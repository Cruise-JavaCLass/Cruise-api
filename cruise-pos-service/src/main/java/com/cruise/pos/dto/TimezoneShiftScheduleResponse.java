package com.cruise.pos.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record TimezoneShiftScheduleResponse(
        String voyageNumber,
        String currentTimezone,
        Integer currentOffsetMinutes,
        OffsetDateTime currentShipClockTime,
        List<TimezoneShiftScheduleItem> plannedPortShifts
) {}
