package org.flow.messaging;

import org.flow.enums.ApprovalStatus;

public record ApprovalEvent(
        String type,
        Long purchaseId,
        ApprovalStatus decision
) {}
