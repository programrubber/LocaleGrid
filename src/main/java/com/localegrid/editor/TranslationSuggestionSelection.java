package com.localegrid.editor;

import com.localegrid.llm.TranslationSuggestionSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Locks a request's candidate identity across locales without writing other locale values. */
final class TranslationSuggestionSelection {
    private final List<TranslationSuggestionSet> sets;
    private final List<TranslationSuggestionChoices> choices = new ArrayList<>();
    private String selectedId;

    TranslationSuggestionSelection(List<TranslationSuggestionSet> sets) { this.sets = sets; }

    TranslationSuggestionChoices addLocale(String locale, Consumer<String> onApply,
                                           Consumer<Boolean> onVisibilityChanged) {
        Map<String, String> candidates = new LinkedHashMap<>();
        for (TranslationSuggestionSet set : sets) {
            String text = set.translations().get(locale);
            if (text != null) candidates.put(set.id(), text);
        }
        TranslationSuggestionChoices panel = new TranslationSuggestionChoices(locale, candidates, (id, text) -> {
            if (selectedId != null && !selectedId.equals(id)) return;
            selectedId = id;
            for (TranslationSuggestionChoices other : choices) other.retainCandidate(id);
            onApply.accept(text);
        }, onVisibilityChanged);
        choices.add(panel);
        return panel;
    }
}
