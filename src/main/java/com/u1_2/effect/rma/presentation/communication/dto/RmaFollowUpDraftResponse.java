package com.u1_2.effect.rma.presentation.communication.dto;

import com.u1_2.effect.rma.presentation.communication.dto.CreateRmaCommunicationRequest.Source;

public record RmaFollowUpDraftResponse(String rmaId, String analysisId, String messageText, Source source) {
}
