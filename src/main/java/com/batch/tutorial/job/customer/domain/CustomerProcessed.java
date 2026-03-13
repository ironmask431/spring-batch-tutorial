package com.batch.tutorial.job.customer.domain;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 정제된 고객 데이터 (OUTPUT)
 * customers_processed 테이블에 저장
 *
 * ℹ️ JPA Entity 대신 일반 DTO로 사용 (Writer에서 JdbcTemplate으로 직접 삽입)
 *    → Spring Batch에서는 JPA보다 JDBC가 성능상 유리한 경우가 많음
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class CustomerProcessed {

    private Long customerId;   // 원본 customers.id
    private String name;       // 정규화된 이름 (공백 제거)
    private String email;      // 소문자 변환 + 유효성 검사 완료
    private String phone;      // 숫자만 추출
    private Integer age;
    private LocalDateTime processedAt;
}
