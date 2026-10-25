package com.cruise.pos.dto;

import java.time.Instant;
import java.util.List;

public record TimezoneShiftScheduleItem(
        String portName,
        String timezone,
        Integer clockOffsetMinutes,
        Instant scheduledTransitionTime,
        String status
) {}
