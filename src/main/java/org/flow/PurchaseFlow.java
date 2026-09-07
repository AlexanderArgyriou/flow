package org.flow;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;
import org.flow.domain.Purchase;
import org.flow.enums.ApprovalStatus;
import org.flow.enums.PurchaseStatus;
import org.flow.messaging.ApprovalEvent;
import org.flow.resource.ApprovalResource;
import org.flow.resource.PurchaseResource;
import org.flow.service.PurchaseService;

import java.util.Map;

import static io.quarkiverse.flow.dsl.FlowDSL.*;

@ApplicationScoped
public class PurchaseFlow extends Flow {
    private final PurchaseService purchaseService;

    public PurchaseFlow(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("purchase").tasks(
                function("createPurchase", this::createPurchase),
                function("validatePurchase", this::validatePurchase),
                function("reserveInventory", this::reserveInventory),
                function("waitingApproval", this::waitingApproval),
                listen(
                        "waitForApproval",
                        toOne(
                                consumed("org.flow.approval")
                                        .dataAs(ApprovalEvent.class, (event, wfCtx, taskCtx) -> {
                                            Purchase current = taskCtx.input().as(Purchase.class).orElseThrow();
                                            return event.purchaseId().equals(current.id);
                                        })
                        )
                ),
                switchWhenOrElse(
                        (ApprovalEvent event) ->
                                ApprovalStatus.APPROVED.equals(event.decision()),
                        "approved",
                        "rejected",
                        ApprovalEvent.class
                ),
                function("rejected", this::rejected),
                function("approved", this::approved),
                function("createSupplierOrder", this::createSupplierOrder),
                function("completePurchase", this::complete)
        ).build();
    }

    private Purchase createPurchase(
            PurchaseResource.CreatePurchaseRequest request) {
        return purchaseService.create(
                request.requester(),
                request.description(),
                request.supplier(),
                request.total()
        );
    }

    private Purchase validatePurchase(Purchase purchase) {
        return purchaseService.setStatus(
                purchase.id,
                PurchaseStatus.VALIDATING
        );
    }

    private Purchase reserveInventory(Purchase purchase) {
        return purchaseService.reserveStock(
                purchase.id
        );
    }

    private Purchase waitingApproval(Purchase purchase) {
        return purchaseService.setStatus(
                purchase.id,
                PurchaseStatus.WAITING_APPROVAL
        );
    }

    private Purchase approved(ApprovalEvent event) {
        return purchaseService.setStatus(
                event.purchaseId(),
                PurchaseStatus.APPROVED
        );
    }

    private Purchase rejected(ApprovalEvent event) {
        return purchaseService.reject(
                event.purchaseId()
        );
    }

    private Purchase createSupplierOrder(Purchase purchase) {
        return purchaseService.createSupplierOrder(
                purchase.id
        );
    }

    private Purchase complete(Purchase purchase) {
        return purchaseService.complete(
                purchase.id
        );
    }
}
