package com.localegrid.model;

public class Diagnostic {
    public enum Severity {
        ERROR,
        WARNING
    }

    private final Severity severity;
    private final String message;
    private final String key;
    private final String locale;

    public Diagnostic(Severity severity, String message, String key) {
        this(severity, message, key, null);
    }

    public Diagnostic(Severity severity, String message, String key, String locale) {
        this.severity = severity;
        this.message = message;
        this.key = key;
        this.locale = locale;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getKey() {
        return key;
    }

    public String getLocale() {
        return locale;
    }
}
