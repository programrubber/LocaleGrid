package com.localegrid.model;

public class Diagnostic {
    public enum Severity {
        ERROR,
        WARNING
    }

    private final Severity severity;
    private final String message;
    private final String key;

    public Diagnostic(Severity severity, String message, String key) {
        this.severity = severity;
        this.message = message;
        this.key = key;
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
}
