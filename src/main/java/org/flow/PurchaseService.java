package org.flow;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.math.BigDecimal;

@ApplicationScoped
public class PurchaseService {

    private static final Logger log = Logger.getLogger(PurchaseService.class);

    public Purchase create(
            String requester,
            String description,
            String supplier,
            BigDecimal total) {

        Purchase purchase = new Purchase();

        purchase.requester = requester;
        purchase.description = description;
        purchase.supplier = supplier;
        purchase.total = total;
        purchase.status = PurchaseFlow.PurchaseStatus.CREATED;

        purchase.persist();

        return purchase;
    }

    
    public Purchase setStatus(
            Long id,
            PurchaseFlow.PurchaseStatus status) {

        Purchase purchase = Purchase.<Purchase>findById(id);

        if (purchase == null) {
            throw new NotFoundException(
                    "Purchase " + id + " not found"
            );
        }

        purchase.status = status;

        return purchase;
    }

    
    public Purchase reserveStock(Long purchaseId) {

        Purchase purchase = Purchase.<Purchase>findById(purchaseId);

        if (purchase == null) {
            throw new NotFoundException(
                    "Purchase " + purchaseId + " not found"
            );
        }

        log.infof(
                "RESERVING STOCK FOR PURCHASE %d",
                purchaseId
        );

        purchase.status =
                PurchaseFlow.PurchaseStatus.STOCK_RESERVED;

        return purchase;
    }

    
    public Purchase createSupplierOrder(Long purchaseId) {

        Purchase purchase = Purchase.<Purchase>findById(purchaseId);

        if (purchase == null) {
            throw new NotFoundException(
                    "Purchase " + purchaseId + " not found"
            );
        }

        log.infof(
                "CREATING SUPPLIER ORDER FOR %d",
                purchaseId
        );

        purchase.status =
                PurchaseFlow.PurchaseStatus.SUPPLIER_ORDERED;

        return purchase;
    }

    
    public Purchase complete(Long purchaseId) {

        Purchase purchase = Purchase.<Purchase>findById(purchaseId);

        if (purchase == null) {
            throw new NotFoundException(
                    "Purchase " + purchaseId + " not found"
            );
        }

        log.infof(
                "COMPLETING PURCHASE %d",
                purchaseId
        );

        purchase.status =
                PurchaseFlow.PurchaseStatus.COMPLETED;

        return purchase;
    }

    
    public Purchase reject(Long purchaseId) {

        Purchase purchase = Purchase.<Purchase>findById(purchaseId);

        if (purchase == null) {
            throw new NotFoundException(
                    "Purchase " + purchaseId + " not found"
            );
        }

        log.infof(
                "REJECTING PURCHASE %d",
                purchaseId
        );

        purchase.status =
                PurchaseFlow.PurchaseStatus.REJECTED;

        return purchase;
    }
}