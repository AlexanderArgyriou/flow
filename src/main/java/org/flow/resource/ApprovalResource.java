package org.flow.resource;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.flow.ApprovalFlow;
import org.flow.enums.ApprovalStatus;

import java.util.Map;

@Path("/approval")
public class ApprovalResource {
    private final ApprovalFlow approvalFlow;

    public ApprovalResource(ApprovalFlow approvalFlow) {
        this.approvalFlow = approvalFlow;
    }

    public record ApprovalEvent(
            String type,
            Long purchaseId,
            ApprovalStatus decision
    ) {
    }

    public record ApprovalRequest(
            Long purchaseId,
            ApprovalStatus decision
    ) {
    }

    @POST
    public Uni<Map<String, Object>> approve(ApprovalRequest request) {
        return approvalFlow.startInstance(request)
                .map(wfm -> wfm.asMap().orElseThrow());
    }
}
