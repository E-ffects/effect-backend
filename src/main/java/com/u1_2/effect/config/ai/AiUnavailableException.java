package com.u1_2.effect.config.ai;

import com.u1_2.effect.global.exception.ApiException;

public class AiUnavailableException extends ApiException {

    public AiUnavailableException() {
        super(AiErrorCode.AI_UNAVAILABLE);
    }
}
