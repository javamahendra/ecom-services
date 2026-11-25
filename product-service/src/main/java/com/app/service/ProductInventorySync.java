package com.app.service;

import com.app.entity.Product;
import com.app.entity.ProductVariant;
import com.app.event.StockSyncEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductInventorySync {
    private static final Logger log = LoggerFactory.getLogger(ProductInventorySync.class);
    private final KafkaTemplate<Object, Object> kafka;

    public ProductInventorySync(KafkaTemplate<Object, Object> kafka) {
        this.kafka = kafka;
    }

    public void publishSnapshot(Product product) {
        product.getVariants().forEach(variant -> send(StockSyncEvent.seed(
                product.getId(), product.getProductCode(), variant.getId(), product.getName(),
                variant.getColor(), variant.getStockCount())));
    }

    public void reconcileSnapshot(Product product, List<CompletableFuture<?>> acknowledgements) {
        product.getVariants().forEach(variant -> acknowledgements.add(kafka.send(
                "commerce.product-stock-events",
                product.getId() + ":" + variant.getId(),
                StockSyncEvent.reconcile(product.getId(), product.getProductCode(), variant.getId(), product.getName(),
                        variant.getColor(), variant.getStockCount()))));
    }

    public void publishMetadataSnapshot(Product product, List<CompletableFuture<?>> acknowledgements) {
        product.getVariants().forEach(variant -> acknowledgements.add(kafka.send(
                "commerce.product-stock-events",
                product.getId() + ":" + variant.getId(),
                StockSyncEvent.metadata(product.getId(), product.getProductCode(), variant.getId(),
                        product.getName(), variant.getColor(), variant.getStockCount()))));
    }

    public void publish(Product product, ProductVariant variant) {
        send(StockSyncEvent.update(product.getId(), product.getProductCode(), variant.getId(), product.getName(),
                variant.getColor(), variant.getStockCount()));
    }

    public void publishMetadata(Product product, ProductVariant variant) {
        send(StockSyncEvent.metadata(product.getId(), product.getProductCode(), variant.getId(), product.getName(),
                variant.getColor(), variant.getStockCount()));
    }

    public void remove(String productId, String productCode, String variantId) {
        send(StockSyncEvent.remove(productId, productCode, variantId));
    }

    private void send(StockSyncEvent event) {
        CompletableFuture<?> result = kafka.send("commerce.product-stock-events", event.key(), event);
        result.whenComplete((sent, error) -> {
            if (error != null) {
                log.error("Failed to publish product stock synchronization: productId={}, variantId={}, type={}",
                        event.productId(), event.variantId(), event.eventType(), error);
            }
        });
    }
}
