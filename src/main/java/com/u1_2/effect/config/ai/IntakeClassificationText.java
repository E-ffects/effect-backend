package com.u1_2.effect.config.ai;

import java.util.List;

public final class IntakeClassificationText {

    private static final String REPLY_MARKER = "\n\n[고객 보충 회신]\n";

    private IntakeClassificationText() {
    }

    public static String withSupplements(String rawText, List<String> replies) {
        if (replies.isEmpty()) {
            return rawText;
        }
        // DB의 원문은 그대로 두고 AI 요청에만 보충 회신을 덧붙임
        return rawText + REPLY_MARKER + String.join(REPLY_MARKER, replies);
    }

    static String latestSection(String text) {
        int marker = text.lastIndexOf(REPLY_MARKER);
        return marker < 0 ? text : text.substring(marker + REPLY_MARKER.length());
    }
}
