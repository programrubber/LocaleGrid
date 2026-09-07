package com.localegrid.llm;

/** Output form, independent of translation length. */
public enum TranslationStyle {
    PRESERVE("자동", "원문의 표현 형태에 맞춰 번역합니다.", """
        Preserve the source's form, intent, and tone in the network UI context.
        Do not arbitrarily change the role of a name, action, state, sentence, question, or warning.
        """),
    LABEL("라벨형", "화면 역할에 맞는 항목명·동작·상태로 표현하며, 의미를 축약하거나 상태를 추측하지 않습니다.", """
        Translate according to the UI role. Use noun phrases for names, properties, and columns;
        action wording for execution buttons; and state values only when the source supplies the actual state.
        For setting controls, use clear feature names or setting actions according to the product convention.
        Do not force every toggle label into an imperative.
        If the UI role is unknown and the source names a property, use neutral property wording.
        Do not omit the subject, conditions, negation, or scope, or assert a state not given by the source.
        Omit literal Whether/If or redundant Status only when the original meaning remains clear.
        Follow the product's casing convention; otherwise use English Title Case for labels.
        Use each target language's own UI conventions, not English casing in other languages.
        Labels are not summaries. Labels may be as long as needed; do not abbreviate or impose a length limit.
        """),
    SENTENCE("문장형", "설명·질문·경고·로그의 역할을 유지해 문장으로 표현하며, 없는 원인이나 사실을 추가하지 않습니다.", """
        Preserve the source's role as help text, a question, warning, or audit message
        while translating into natural complete sentences. Preserve technical meaning and conditions.
        Do not add causes, behavior, risks, effects, actors, or success claims absent from the source.
        Preserve distinctions between State, Event, Fault, and Alarm when present.
        A standalone Whether-clause is not a complete sentence.
        Accuracy takes priority when completing a fragment would require inventing facts,
        a question, or a setting function. Do not invent content merely to complete a sentence.
        """);

    private final String label;
    private final String description;
    private final String instruction;

    TranslationStyle(String label, String description, String instruction) {
        this.label = label;
        this.description = description;
        this.instruction = instruction.stripIndent().trim();
    }

    public String label() { return label; }
    public String description() { return description; }
    public String instruction() { return instruction; }
}
