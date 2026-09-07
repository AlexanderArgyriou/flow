package org.flow.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_items")
public class PurchaseItem extends PanacheEntity {
    public String productCode;
    public String productName;
    public int quantity;
    public BigDecimal unitPrice;

    @ManyToOne
    public Purchase purchase;

    public BigDecimal total() {
        return unitPrice.multiply(
                BigDecimal.valueOf(quantity)
        );
    }
}
