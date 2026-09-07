package com.localegrid.llm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One wording choice translated consistently across every requested locale. */
public record TranslationSuggestionSet(String id, Map<String, String> translations) {
    public TranslationSuggestionSet {
        translations = Collections.unmodifiableMap(new LinkedHashMap<>(translations));
    }
}
