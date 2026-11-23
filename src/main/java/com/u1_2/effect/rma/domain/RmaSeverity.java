package com.u1_2.effect.rma.domain;

public enum RmaSeverity {

    CRITICAL(9, 100, "CRITICAL"),
    HIGH(7, 70, "HIGH"),
    MEDIUM(5, 40, "MEDIUM"),
    LOW(1, 10, "LOW"),
    UNDECIDED(0, 0, "판정 보류");

    private final int minimum;
    private final int priorityScore;
    private final String label;

    RmaSeverity(int minimum, int priorityScore, String label) {
        this.minimum = minimum;
        this.priorityScore = priorityScore;
        this.label = label;
    }

    public int minimum() {
        return minimum;
    }

    public int priorityScore() {
        return priorityScore;
    }

    public String label() {
        return label;
    }

    // 조사 순위·패턴 상세·고위험 알림이 같은 원본 심각도 구간을 사용함
    public static RmaSeverity from(int value) {
        for (var severity : values()) {
            if (value >= severity.minimum) {
                return severity;
            }
        }
        return UNDECIDED;
    }
}
