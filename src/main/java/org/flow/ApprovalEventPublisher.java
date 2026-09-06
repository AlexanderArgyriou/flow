//package org.flow;
//
//import jakarta.enterprise.context.ApplicationScoped;
//import jakarta.ws.rs.*;
//import jakarta.ws.rs.core.MediaType;
//import org.eclipse.microprofile.reactive.messaging.Channel;
//import org.eclipse.microprofile.reactive.messaging.Emitter;
//
//import java.util.Map;
//
//@ApplicationScoped
//public class ApprovalEventPublisher {
//
//    @Channel("flow-out")
//    Emitter<Map<String, Object>> emitter;
//
//    public void approve(Long purchaseId) {
//
//        emitter.send(
//                Map.of(
//                        "type",
//                        "acme.purchase.approval.v1",
//
//                        "purchaseId",
//                        purchaseId,
//
//                        "decision",
//                        "APPROVED"
//                )
//        );
//    }
//
//    public void reject(Long purchaseId) {
//
//        emitter.send(
//                Map.of(
//                        "type",
//                        "acme.purchase.approval.v1",
//
//                        "purchaseId",
//                        purchaseId,
//
//                        "decision",
//                        "REJECTED"
//                )
//        );
//    }
//}