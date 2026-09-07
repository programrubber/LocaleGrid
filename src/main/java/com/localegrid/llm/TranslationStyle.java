package com.localegrid.llm;

/** Output form, independent of translation length. */
public enum TranslationStyle {
    PRESERVE("자동", "원문의 표현 형태에 맞춰 번역합니다.",
        "Preserve the reference text's word, phrase, or sentence form and tone."),
    LABEL("라벨형", "버튼·메뉴·항목명에 맞게 표현하며, 길이 제한이나 의미 축약은 하지 않습니다.",
        "Use wording suitable for a button, menu, or item label. Preserve the full meaning; "
            + "do not shorten, abbreviate, omit meaning, or impose a length limit. "
            + "Labels may be as long as needed. Meaning preservation and label form take priority over brevity."),
    SENTENCE("문장형", "자연스러운 완결 문장으로 표현하며, 불필요한 설명을 추가하지 않습니다.",
        "Use natural complete sentences faithful to the meaning. Do not add unnecessary explanations or content.");

    private final String label;
    private final String description;
    private final String instruction;

    TranslationStyle(String label, String description, String instruction) {
        this.label = label;
        this.description = description;
        this.instruction = instruction;
    }

    public String label() { return label; }
    public String description() { return description; }
    public String instruction() { return instruction; }
}
