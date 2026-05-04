package com.storybook.aikidstorybook.service;

import org.springframework.stereotype.Service;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

import java.util.regex.Pattern;

@Service
public class InputSanitizationService {

    private static final PolicyFactory HTML_POLICY = new HtmlPolicyBuilder()
            .allowElements("b", "i", "em", "strong", "p", "br", "ul", "li", "ol")
            .allowAttributes("class").matching(Pattern.compile("[a-zA-Z0-9-_]+")).onElements("p", "ul", "li", "ol")
            .disallowElements("script", "iframe", "object", "embed", "style")
            .toFactory();

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 5000;
    private static final int MAX_PAGE_TEXT_LENGTH = 1000;
    private static final int MAX_CUSTOM_FIELD_LENGTH = 500;

    // XSS patterns to detect potential attacks
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("(?i)<script[^>]*>.*?</script>");
    private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile("(?i)\\bon[a-z]+\\s*=");
    private static final Pattern DATA_BINDING_PATTERN = Pattern.compile("(?i){{.*?}}|\\${.*?}|<%.*?%>");
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile("(?i)(;\\s*(DROP|DELETE|INSERT|UPDATE|CREATE|ALTER|EXEC|EXECUTE)|--|/\\*|\\*/|UNION|SELECT)");

    /**
     * Sanitize and validate book title
     */
    public String sanitizeTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be empty");
        }

        String sanitized = sanitizeBasicInput(title);
        
        if (sanitized.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Title exceeds maximum length of " + MAX_TITLE_LENGTH);
        }

        return sanitized;
    }

    /**
     * Sanitize and validate book description
     */
    public String sanitizeDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return "";
        }

        String sanitized = sanitizeBasicInput(description);
        
        if (sanitized.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description exceeds maximum length of " + MAX_DESCRIPTION_LENGTH);
        }

        return sanitized;
    }

    /**
     * Sanitize and validate page text content
     */
    public String sanitizePageText(String pageText) {
        if (pageText == null || pageText.trim().isEmpty()) {
            return "";
        }

        String sanitized = sanitizeBasicInput(pageText);
        
        if (sanitized.length() > MAX_PAGE_TEXT_LENGTH) {
            throw new IllegalArgumentException("Page text exceeds maximum length of " + MAX_PAGE_TEXT_LENGTH);
        }

        return sanitized;
    }

    /**
     * Sanitize and validate custom fields (outline, characters, setting, etc.)
     */
    public String sanitizeCustomField(String field, String fieldName) {
        if (field == null || field.trim().isEmpty()) {
            return "";
        }

        String sanitized = sanitizeBasicInput(field);
        
        if (sanitized.length() > MAX_CUSTOM_FIELD_LENGTH) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum length of " + MAX_CUSTOM_FIELD_LENGTH);
        }

        return sanitized;
    }

    /**
     * Sanitize HTML content for rich text fields
     */
    public String sanitizeHtmlContent(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            return "";
        }

        // First check for injection patterns
        if (containsInjectionPatterns(htmlContent)) {
            throw new IllegalArgumentException("HTML content contains potentially malicious patterns");
        }

        // Apply OWASP policy
        return HTML_POLICY.sanitize(htmlContent);
    }

    /**
     * Basic input sanitization - removes whitespace, escapes special chars
     */
    private String sanitizeBasicInput(String input) {
        if (input == null) {
            return "";
        }

        // Check for injection patterns
        if (containsInjectionPatterns(input)) {
            throw new IllegalArgumentException("Input contains potentially malicious patterns");
        }

        // Trim whitespace
        String sanitized = input.trim();

        // Remove null bytes
        sanitized = sanitized.replace("\0", "");

        // Remove control characters except newlines and tabs
        sanitized = sanitized.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");

        // Normalize whitespace
        sanitized = sanitized.replaceAll("\\s+", " ");

        return sanitized;
    }

    /**
     * Check if input contains potential injection patterns
     */
    private boolean containsInjectionPatterns(String input) {
        if (input == null) {
            return false;
        }

        return SCRIPT_PATTERN.matcher(input).find()
                || EVENT_HANDLER_PATTERN.matcher(input).find()
                || DATA_BINDING_PATTERN.matcher(input).find()
                || SQL_INJECTION_PATTERN.matcher(input).find();
    }

    /**
     * Validate dropdown/enum values
     */
    public String validateEnumValue(String value, String[] allowedValues, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }

        String trimmed = value.trim();
        for (String allowed : allowedValues) {
            if (allowed.equals(trimmed)) {
                return trimmed;
            }
        }

        throw new IllegalArgumentException(fieldName + " contains invalid value: " + trimmed);
    }

    /**
     * Validate numeric range
     */
    public int validateNumberInRange(Integer number, int min, int max, String fieldName) {
        if (number == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }

        if (number < min || number > max) {
            throw new IllegalArgumentException(fieldName + " must be between " + min + " and " + max);
        }

        return number;
    }
}
