package org.flow;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;


import java.math.BigDecimal;

@Entity
@Table(name = "purchases")
public class Purchase extends PanacheEntity {
    public String description;
    public String requester;
    public String supplier;
    public BigDecimal total;
    @Enumerated(EnumType.STRING)
    public PurchaseFlow.PurchaseStatus status;
    public String workflowInstanceId;
}
