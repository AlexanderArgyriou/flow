package org.flow;

import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.mutiny.Uni;
import io.smallrye.common.annotation.Blocking;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;


import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Path("/purchases")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PurchaseResource {
    private final PurchaseService purchaseService;
    private final PurchaseFlow purchaseFlow;

    public PurchaseResource(PurchaseService purchaseService, PurchaseFlow purchaseFlow) {
        this.purchaseService = purchaseService;
        this.purchaseFlow = purchaseFlow;
    }

    @POST
    @Blocking
    @Transactional
    public Uni<Map<String, Object>> create(
            CreatePurchaseRequest request) {
        return Uni.createFrom().item(() ->
                        purchaseService.create(
                                request.requester(),
                                request.description(),
                                request.supplier(),
                                request.total()
                        )
                )
                .map(p -> Map.of("purchaseId", p.id))
                .chain(ctx -> purchaseFlow.startInstance(ctx))
                .map(r -> r.asMap().orElseThrow());

    }


    @GET
    @Path("/{id}")
    public Uni<Purchase> get(@PathParam("id") Long id) {
        return Uni.createFrom().item(() -> Purchase.<Purchase>findById(id))
                .onItem()
                .ifNull()
                .failWith(() ->
                        new NotFoundException(
                                "Purchase " + id + " not found"
                        )
                );
    }

    public record CreatePurchaseRequest(
            String requester,
            String description,
            String supplier,
            BigDecimal total
    ) {
    }
}
