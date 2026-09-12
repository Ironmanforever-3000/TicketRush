package com.ticketrush.hold;

import com.ticketrush.hold.dto.HoldRequest;
import com.ticketrush.hold.dto.HoldResponse;

public interface HoldService {
    HoldResponse createHold(Long showId, HoldRequest request);
}
