package org.flow.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.flow.PurchaseFlow;
import org.flow.domain.Purchase;
import org.flow.enums.PurchaseStatus;
import org.jboss.logging.Logger;

import java.math.BigDecimal;

@ApplicationScoped
public class PurchaseService {

    private static final Logger log = Logger.getLogger(PurchaseService.class);

    @Transactional
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
        purchase.status = PurchaseStatus.CREATED;

        purchase.persist();

        return purchase;
    }

    @Transactional
    public Purchase setStatus(
            Long id,
            PurchaseStatus status) {

        Purchase purchase = Purchase.<Purchase>findById(id);

        if (purchase == null) {
            throw new NotFoundException(
                    "Purchase " + id + " not found"
            );
        }

        purchase.status = status;

        return purchase;
    }


    @Transactional
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
                PurchaseStatus.STOCK_RESERVED;

        return purchase;
    }


    @Transactional
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
                PurchaseStatus.SUPPLIER_ORDERED;

        return purchase;
    }


    @Transactional
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
                PurchaseStatus.COMPLETED;

        return purchase;
    }


    @Transactional
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
                PurchaseStatus.REJECTED;

        return purchase;
    }
}