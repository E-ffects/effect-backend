package com.u1_2.effect.patternreport.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportDepartment {
    PROCESS_ENGINEERING("공정기술팀", "공정 조건과 규격 이탈, 대책 시행 후 추이를 확인해 주세요."),
    QUALITY("품질팀", "검출 누락 원인과 개정 검사 기준의 검출 효과를 확인해 주세요."),
    EQUIPMENT("설비팀", "관련 설비의 이상 이력과 부품 점검·교체 기록을 확인해 주세요."),
    PRODUCTION("생산팀", "관련 생산 Lot의 선별 범위와 잔여 수량을 확인해 주세요."),
    PURCHASING("구매팀", "관련 자재 Lot의 공급사와 투입 범위, 입고검사 이력을 확인해 주세요.");

    private final String label;
    private final String defaultRequest;
}
