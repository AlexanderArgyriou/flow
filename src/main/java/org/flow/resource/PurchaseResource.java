package org.flow.resource;

import io.smallrye.mutiny.Uni;
import io.smallrye.common.annotation.Blocking;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.flow.PurchaseFlow;
import org.flow.service.PurchaseService;
import org.flow.domain.Purchase;


import java.math.BigDecimal;
import java.util.Map;

@Path("/purchases")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PurchaseResource {
    private final PurchaseFlow purchaseFlow;

    public PurchaseResource(
            PurchaseFlow purchaseFlow) {
        this.purchaseFlow = purchaseFlow;
    }

    public record CreatePurchaseRequest(
            String requester,
            String description,
            String supplier,
            BigDecimal total
    ) {}

    @POST
    public Uni<Map<String, Object>> create(
            CreatePurchaseRequest request) {
        return purchaseFlow.startInstance(request)
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
}
