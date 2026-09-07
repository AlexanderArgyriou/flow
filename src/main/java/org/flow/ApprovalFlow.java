package org.flow;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;
import org.flow.messaging.ApprovalEvent;
import org.flow.resource.ApprovalResource;

import static io.quarkiverse.flow.dsl.FlowDSL.emitJson;
import static io.quarkiverse.flow.dsl.FlowDSL.function;

@ApplicationScoped
public class ApprovalFlow extends Flow {
    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("approval").tasks(
                function("buildApproval", this::buildApproval),
                emitJson("org.flow.approval", ApprovalEvent.class)
        ).build();
    }


    private ApprovalEvent buildApproval(ApprovalResource.ApprovalRequest request) {
        return
                new ApprovalEvent(
                        "org.flow.approval",
                        request.purchaseId(),
                        request.decision()
                );
    }
}
