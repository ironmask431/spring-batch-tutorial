package com.batch.tutorial.job.customer;

import com.batch.tutorial.common.exception.InvalidCustomerException;
import com.batch.tutorial.common.listener.JobLoggingListener;
import com.batch.tutorial.job.customer.domain.Customer;
import com.batch.tutorial.job.customer.domain.CustomerProcessed;
import com.batch.tutorial.job.customer.listener.CustomerSkipListener;
import com.batch.tutorial.job.customer.processor.CustomerItemProcessor;
import com.batch.tutorial.job.customer.reader.CustomerRowMapper;
import com.batch.tutorial.job.customer.writer.CustomerItemWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * ====================================================================
 * 고객 데이터 정제 배치 Job 설정
 * ====================================================================
 *
 * [Job 구성]
 * customerCleansingJob
 *   └── customerCleansingStep (Chunk 기반)
 *         ├── Reader    : JdbcCursorItemReader (PENDING 고객 읽기)
 *         ├── Processor : CustomerItemProcessor (유효성 검사 + 변환)
 *         └── Writer    : CustomerItemWriter (DB 저장 + 상태 업데이트)
 *
 * [핵심 개념 - Chunk 기반 처리]
 * ┌─────────────────────────────────────────────────────────────┐
 * │  청크 단위(CHUNK_SIZE=5)로 반복:                             │
 * │  Read → Read → Read → Read → Read → Process(5건) → Write(5건) │
 * │  이후 다음 청크 시작...                                      │
 * └─────────────────────────────────────────────────────────────┘
 *
 * [오류 처리]
 * - InvalidCustomerException 발생 시 → Skip (skipLimit=100까지 허용)
 * - Skip 시 CustomerSkipListener가 상태를 SKIPPED로 업데이트
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CustomerCleansingJobConfig {

    // ✅ 청크 크기: 한 번에 읽고-처리하고-쓸 데이터 건수
    private static final int CHUNK_SIZE = 5;

    // ✅ Spring Batch 5부터는 JobBuilderFactory/StepBuilderFactory 대신
    //    JobRepository와 TransactionManager를 직접 주입
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    // ==================================================================
    // JOB 정의
    // ==================================================================

    /**
     * Job = 배치 작업의 최상위 단위
     * 하나 이상의 Step으로 구성
     */
    @Bean
    public Job customerCleansingJob(Step customerCleansingStep) {
        return new JobBuilder("customerCleansingJob", jobRepository)
                .listener(new JobLoggingListener())  // Job 시작/종료 로깅
                .start(customerCleansingStep)         // 첫 번째 Step
                // .next(anotherStep)                 // 여러 Step 연결 가능
                .build();
    }

    // ==================================================================
    // STEP 정의
    // ==================================================================

    /**
     * Step = Job의 실행 단위
     * Chunk 기반: <읽기타입, 쓰기타입>chunk(청크크기, 트랜잭션매니저)
     */
    @Bean
    public Step customerCleansingStep(
            JdbcCursorItemReader<Customer> customerItemReader,
            CustomerSkipListener customerSkipListener) {

        return new StepBuilder("customerCleansingStep", jobRepository)
                // ✅ Chunk 기반 처리 선언: Customer를 읽어 CustomerProcessed로 변환
                .<Customer, CustomerProcessed>chunk(CHUNK_SIZE, transactionManager)

                .reader(customerItemReader)
                .processor(new CustomerItemProcessor())
                .writer(new CustomerItemWriter(jdbcTemplate, namedParameterJdbcTemplate))

                // ✅ 오류 허용 설정 (faultTolerant)
                .faultTolerant()
                    .skip(InvalidCustomerException.class)  // 이 예외는 Skip 처리
                    .skipLimit(100)                         // 최대 100건까지 Skip 허용 (초과 시 Job 실패)
                    .listener(customerSkipListener)         // Skip 발생 시 리스너 실행

                .build();
    }

    // ==================================================================
    // READER 정의
    // ==================================================================

    /**
     * JdbcCursorItemReader: DB 커서 방식으로 데이터를 읽음
     *
     * ✅ JpaPagingItemReader vs JdbcCursorItemReader
     * - JpaPagingItemReader : 페이지 단위 조회, 중간에 데이터 변경 시 누락 위험
     * - JdbcCursorItemReader: 커서로 순차 읽기, 대용량 처리에 안전하고 빠름 ← 선택
     *
     * ✅ @StepScope: Step이 실행될 때마다 새로운 인스턴스 생성
     *    → Job Parameter를 런타임에 주입받을 수 있음 (다음 강의에서 다룸)
     */
    @Bean
    public JdbcCursorItemReader<Customer> customerItemReader() {
        return new JdbcCursorItemReaderBuilder<Customer>()
                .name("customerItemReader")
                .dataSource(dataSource)
                // PENDING 상태의 고객만 읽기 (id 순서 보장)
                .sql("""
                        SELECT id, name, email, phone, age, status, created_at
                        FROM customers
                        WHERE status = 'PENDING'
                        ORDER BY id ASC
                        """)
                .rowMapper(new CustomerRowMapper())
                .build();
    }

    // ==================================================================
    // LISTENER 정의
    // ==================================================================

    @Bean
    public CustomerSkipListener customerSkipListener() {
        return new CustomerSkipListener(namedParameterJdbcTemplate);
    }
}
