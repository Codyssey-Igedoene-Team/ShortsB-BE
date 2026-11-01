package com.shortsb.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonValue;

// 결은 정서적 질감이며 결말과 별도 축
public enum ContrastAxis {
    TEXTURE("texture", "결"), PERSPECTIVE("perspective", "시선"), TONE("tone", "어조"),
    ATTITUDE("attitude", "태도"), ENDING("ending", "결말"), SETTING("setting", "시대·배경");

    private final String code;
    private final String label;

    ContrastAxis(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static ContrastAxis fromCode(String code) {
        for (ContrastAxis axis : values()) {
            if (axis.code.equals(code)) {
                return axis;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 대비 축입니다.");
    }
}
