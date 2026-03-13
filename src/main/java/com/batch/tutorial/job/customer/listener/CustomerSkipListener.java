package com.batch.tutorial.job.customer.listener;

import com.batch.tutorial.job.customer.domain.Customer;
import com.batch.tutorial.job.customer.domain.CustomerProcessed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Skip 발생 시 처리를 담당하는 리스너
 *
 * ✅ 개발 표준: 오류 발생 시 Skip + 로깅 + 상태 업데이트
 *
 * SkipListener<Reader의 타입, Writer의 타입>
 */
@Slf4j
@RequiredArgsConstructor
public class CustomerSkipListener implements SkipListener<Customer, CustomerProcessed> {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    /**
     * Reader 단계에서 Skip 발생 시
     */
    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("⚠ [SKIP-READ] 읽기 단계에서 건너뜀. 원인: {}", t.getMessage());
    }

    /**
     * Processor 단계에서 Skip 발생 시
     * - 원본 customer의 status를 SKIPPED로 업데이트
     */
    @Override
    public void onSkipInProcess(Customer customer, Throwable t) {
        log.warn("⚠ [SKIP-PROCESS] 처리 단계에서 건너뜀. customerId={}, 원인: {}",
                customer.getId(), t.getMessage());

        updateStatusToSkipped(customer.getId());
    }

    /**
     * Writer 단계에서 Skip 발생 시
     */
    @Override
    public void onSkipInWrite(CustomerProcessed item, Throwable t) {
        log.warn("⚠ [SKIP-WRITE] 저장 단계에서 건너뜀. customerId={}, 원인: {}",
                item.getCustomerId(), t.getMessage());

        updateStatusToSkipped(item.getCustomerId());
    }

    private void updateStatusToSkipped(Long customerId) {
        namedParameterJdbcTemplate.update(
                "UPDATE customers SET status = 'SKIPPED' WHERE id = :id",
                new MapSqlParameterSource("id", customerId)
        );
    }
}
