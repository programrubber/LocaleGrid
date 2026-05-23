package com.localegrid.core;

import com.localegrid.model.Diagnostic;
import com.localegrid.model.LocaleGridRow;
import com.localegrid.model.LocaleValue;
import com.localegrid.model.TranslationTable;

import java.util.HashSet;
import java.util.Set;

public final class TableValidator {
    private TableValidator() {
    }

    public static void validate(TranslationTable table) {
        Set<String> seen = new HashSet<>();
        Set<String> normalKeys = new HashSet<>();

        for (LocaleGridRow row : table.getRows()) {
            if (row.isDeleted()) {
                continue;
            }
            String key = row.getKey();
            if (!seen.add(key)) {
                table.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.ERROR, "Duplicated key: " + key, key));
            }
            if (row.isComment()) {
                continue;
            }
            String error = DotPath.validate(key);
            if (error != null) {
                table.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.ERROR, error, key));
            }
            normalKeys.add(key);

            boolean missing = false;
            for (String locale : table.getLocales()) {
                LocaleValue value = row.getValue(locale);
                if (value.getDisplayText().isEmpty()) {
                    missing = true;
                }
                if (value.isPresent() && !value.isEditable()) {
                    table.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.WARNING, "Unsupported non-string value is readonly: " + key, key));
                }
            }
            if (missing) {
                table.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.WARNING, "Missing or empty value: " + key, key));
            }
        }

        for (String key : normalKeys) {
            if (DotPath.conflictsWithAny(key, normalKeys)) {
                table.getDiagnostics().add(new Diagnostic(Diagnostic.Severity.ERROR, "Dot path conflict: " + key, key));
            }
        }
    }
}
