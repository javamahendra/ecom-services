package com.app.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

@Component
public class NotificationTemplateCatalog {
    private final Properties subjects = new Properties();
    private final Configuration freemarker;

    public NotificationTemplateCatalog() throws IOException {
        try (InputStream stream = new ClassPathResource("templates/email/subjects.properties").getInputStream()) {
            subjects.load(stream);
        }
        freemarker = new Configuration(Configuration.VERSION_2_3_34);
        freemarker.setTemplateLoader(new ClassTemplateLoader(NotificationTemplateCatalog.class, "/"));
        freemarker.setDefaultEncoding("UTF-8");
    }

    public String render(String eventType, String channel, String part, java.util.Map<String, String> values) {
        if ("email".equals(channel) && "subject".equals(part)) {
            String subject = subjects.getProperty(eventType);
            if (subject == null) throw new IllegalArgumentException("Notification template is missing: " + eventType + "/email/subject");
            return processInline("subject-" + eventType, subject, values);
        }
        String templateName;
        if ("email".equals(channel) && "body".equals(part)) {
            templateName = "templates/email/" + eventType + ".ftlh";
        } else if ("sms".equals(channel)) {
            templateName = "templates/sms/" + eventType + ".ftl";
        } else {
            throw new IllegalArgumentException("Unsupported notification template channel");
        }
        try {
            Template template = freemarker.getTemplate(templateName);
            StringWriter output = new StringWriter();
            template.process(values, output);
            return output.toString();
        } catch (IOException | TemplateException exception) {
            throw new IllegalArgumentException("Notification template is missing or invalid: " + templateName, exception);
        }
    }

    private String processInline(String name, String source, java.util.Map<String, String> values) {
        try {
            Template template = new Template(name, new StringReader(source), freemarker);
            StringWriter output = new StringWriter();
            template.process(values, output);
            return output.toString();
        } catch (IOException | TemplateException exception) {
            throw new IllegalArgumentException("Invalid notification subject template: " + name, exception);
        }
    }

    public boolean supports(String eventType) {
        return subjects.containsKey(eventType)
                && hasTemplate("templates/email/" + eventType + ".ftlh")
                && hasTemplate("templates/sms/" + eventType + ".ftl");
    }

    private boolean hasTemplate(String name) {
        try {
            freemarker.getTemplate(name);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static java.util.List<String> supportedEventTypes() {
        return java.util.List.of(
                "USER_CREATED", "USER_UPDATED", "USER_DELETED", "FORGOT_PASSWORD",
                "PASSWORD_RESET", "ADDRESS_UPDATED", "ORDER_CREATED", "ORDER_STATUS_UPDATED",
                "ORDER_CANCELLED", "INVENTORY_RESERVED", "INVENTORY_REJECTED");
    }
}
