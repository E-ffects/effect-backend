package com.u1_2.effect.meta.application;

import com.u1_2.effect.meta.domain.MetaRepository;
import com.u1_2.effect.meta.presentation.dto.MetaResponse;
import com.u1_2.effect.meta.presentation.dto.MetaResponse.CustomerOption;
import com.u1_2.effect.meta.presentation.dto.MetaResponse.DetectionStageOption;
import com.u1_2.effect.meta.presentation.dto.MetaResponse.ProductOption;
import com.u1_2.effect.rma.domain.intake.CustomerRequest;
import com.u1_2.effect.rma.domain.intake.DetectionStage;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접수·추이 화면에 필요한 DB 선택지와 고정 선택지를 합침.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetaService {

    private static final List<DetectionStageOption> DETECTION_STAGES = Arrays.stream(DetectionStage.values())
            .map(stage -> new DetectionStageOption(stage.name(), stage.getLabel()))
            .toList();

    private final MetaRepository metaRepository;
    private final Clock appClock;

    public MetaResponse getMeta() {
        List<CustomerOption> customers = metaRepository.findCustomers().stream()
                .map(customer -> new CustomerOption(customer.customerId(), customer.customerName()))
                .toList();
        List<ProductOption> products = metaRepository.findProducts().stream()
                // 접수는 part_no를, 추이 위젯은 동일한 품번을 code로 읽음
                .map(product -> new ProductOption(product.specId(), product.partNo(), product.partNo(), product.productFamily()))
                .toList();

        // 화면의 기본 기간도 대시보드가 집계할 때 사용하는 동일한 앱 기준일을 따름
        return new MetaResponse(customers, products, DETECTION_STAGES, CustomerRequest.labels(),
                metaRepository.findDefectTypes(), LocalDate.now(appClock));
    }
}
