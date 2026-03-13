package com.batch.tutorial.job.customer.domain;

/**
 * 고객 처리 상태
 */
public enum CustomerStatus {
    PENDING,    // 처리 대기 중
    PROCESSED,  // 처리 완료
    SKIPPED     // 유효성 오류로 인해 건너뜀
}
