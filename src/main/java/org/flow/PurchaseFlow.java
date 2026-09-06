package org.flow;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.dsl.FlowWorkflowBuilder;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

import static io.quarkiverse.flow.dsl.FlowDSL.*;

@ApplicationScoped
public class PurchaseFlow extends Flow {
    private final PurchaseService purchaseService;

    public PurchaseFlow(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    public enum PurchaseStatus {
        CREATED,
        VALIDATING,
        WAITING_APPROVAL,
        APPROVED,
        REJECTED,
        STOCK_RESERVED,
        SUPPLIER_ORDERED,
        COMPLETED,
        FAILED
    }

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("purchase").tasks(
                function("validatePurchase", this::validatePurchase),
                function("reserveInventory", this::reserveInventory),
                function("waitingApproval", this::waitingApproval),
                listen(
                        "waitForApproval",
                        toOne(
                                "org.flow.approval"
                        ).first()
                ),
                switchWhenOrElse(
                        (Map<String, Object> ctx) ->
                                "APPROVED".equals(
                                        ctx.get("decision")
                                ),

                        "approved",
                        "rejected"
                ),
                function("approved", this::approved),
                function("createSupplierOrder", this::createSupplierOrder),
                function("completePurchase", this::complete)
        ).build();
    }

    private Map<String, Object> approved(Map<String, Object> ctx) {
        Long purchaseId =
                ((Number) ctx.get("purchaseId"))
                        .longValue();

        purchaseService.setStatus(
                purchaseId,
                PurchaseStatus.APPROVED
        );

        return ctx;
    }

    private Map<String, Object> validatePurchase(Map<String, Object> ctx) {
        Long purchaseId =
                ((Number) ctx.get("purchaseId"))
                        .longValue();

        purchaseService.setStatus(
                purchaseId,
                PurchaseStatus.VALIDATING
        );

        return ctx;
    }

    private Map<String, Object> reserveInventory(Map<String, Object> ctx) {
        Long purchaseId =
                ((Number) ctx.get("purchaseId"))
                        .longValue();

        purchaseService.reserveStock(
                purchaseId
        );

        return ctx;
    }

    private Map<String, Object> waitingApproval(Map<String, Object> ctx) {
        Long purchaseId =
                ((Number) ctx.get("purchaseId"))
                        .longValue();

        purchaseService.setStatus(
                purchaseId,
                PurchaseStatus.WAITING_APPROVAL
        );

        return ctx;
    }

    private Map<String, Object> createSupplierOrder(Map<String, Object> ctx) {
        Long purchaseId =
                ((Number) ctx.get("purchaseId"))
                        .longValue();

        purchaseService
                .createSupplierOrder(
                        purchaseId
                );

        return ctx;
    }

    private Map<String, Object> complete(Map<String, Object> ctx) {
        Long purchaseId =
                ((Number) ctx.get("purchaseId"))
                        .longValue();

        purchaseService.complete(
                purchaseId
        );

        return ctx;
    }

    private Map<String, Object> rejected(Map<String, Object> ctx) {
        Long purchaseId =
                ((Number) ctx.get("purchaseId"))
                        .longValue();

        purchaseService.reject(
                purchaseId
        );

        return ctx;
    }
}
