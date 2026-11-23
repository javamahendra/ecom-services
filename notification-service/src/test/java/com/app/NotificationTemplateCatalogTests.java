package com.app;

import com.app.service.NotificationTemplateCatalog;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationTemplateCatalogTests {
    @Test
    void everySupportedNotificationHasEmailAndSmsTemplates() throws Exception {
        NotificationTemplateCatalog templates = new NotificationTemplateCatalog();
        Map<String, String> values = Map.of(
                "name", "Taylor",
                "username", "taylor",
                "orderId", "order-1",
                "status", "CONFIRMED",
                "total", "20.00",
                "items", "Item x1",
                "trackingNumber", "track-1",
                "message", "Status changed");
        for (String event : new String[] {
                "USER_CREATED", "USER_UPDATED", "USER_DELETED", "FORGOT_PASSWORD",
                "PASSWORD_RESET", "ADDRESS_UPDATED", "ORDER_CREATED", "ORDER_STATUS_UPDATED",
                "ORDER_CANCELLED", "INVENTORY_RESERVED", "INVENTORY_REJECTED"}) {
            assertFalse(templates.render(event, "email", "subject", values).isBlank(), event);
            assertFalse(templates.render(event, "email", "body", values).isBlank(), event);
            String sms = templates.render(event, "sms", null, values);
            assertFalse(sms.isBlank(), event);
            assertTrue(!sms.contains("{name}") && !sms.contains("{orderId}")
                    && !sms.contains("${name}") && !sms.contains("${orderId}"), event);
        }
    }
}
