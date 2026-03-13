package com.batch.tutorial.job.customer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

/**
 * Spring Batch 메타 테이블에 기록된 실행 결과를 직접 조회하는 테스트
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class BatchMetaDataQueryTest {

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired private JobRepositoryTestUtils jobRepositoryTestUtils;
    @Autowired private Job customerCleansingJob;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM customers_processed");
        jdbcTemplate.update("UPDATE customers SET status = 'PENDING'");
        jobRepositoryTestUtils.removeJobExecutions();
        jobLauncherTestUtils.setJob(customerCleansingJob);
    }

    @Test
    @DisplayName("배치 실행 후 메타 테이블에 기록된 결과 조회")
    void 메타테이블_결과조회() throws Exception {
        // Job 실행
        jobLauncherTestUtils.launchJob();

        // ================================================================
        // 1. BATCH_JOB_INSTANCE - Job 식별 정보
        // ================================================================
        System.out.println("\n");
        System.out.println("====================================================");
        System.out.println(" [1] BATCH_JOB_INSTANCE - Job 식별 정보");
        System.out.println("====================================================");
        List<Map<String, Object>> jobInstances = jdbcTemplate.queryForList(
                "SELECT JOB_INSTANCE_ID, JOB_NAME, JOB_KEY FROM BATCH_JOB_INSTANCE ORDER BY JOB_INSTANCE_ID DESC LIMIT 1"
        );
        jobInstances.forEach(row -> {
            System.out.println("  JOB_INSTANCE_ID : " + row.get("JOB_INSTANCE_ID"));
            System.out.println("  JOB_NAME        : " + row.get("JOB_NAME"));
        });

        // ================================================================
        // 2. BATCH_JOB_EXECUTION - Job 실행 결과
        // ================================================================
        System.out.println("\n----------------------------------------------------");
        System.out.println(" [2] BATCH_JOB_EXECUTION - Job 실행 결과");
        System.out.println("----------------------------------------------------");
        List<Map<String, Object>> jobExecutions = jdbcTemplate.queryForList("""
                SELECT JOB_EXECUTION_ID, STATUS, EXIT_CODE,
                       START_TIME, END_TIME,
                       EXTRACT(EPOCH FROM (END_TIME - START_TIME)) * 1000 AS DURATION_MS
                FROM BATCH_JOB_EXECUTION
                ORDER BY JOB_EXECUTION_ID DESC LIMIT 1
                """);
        jobExecutions.forEach(row -> {
            System.out.println("  JOB_EXECUTION_ID : " + row.get("JOB_EXECUTION_ID"));
            System.out.println("  STATUS           : " + row.get("STATUS"));
            System.out.println("  EXIT_CODE        : " + row.get("EXIT_CODE"));
            System.out.println("  START_TIME       : " + row.get("START_TIME"));
            System.out.println("  END_TIME         : " + row.get("END_TIME"));
            System.out.println("  DURATION_MS      : " + row.get("DURATION_MS"));
        });

        // ================================================================
        // 3. BATCH_STEP_EXECUTION - Step 실행 결과 (핵심!)
        // ================================================================
        System.out.println("\n----------------------------------------------------");
        System.out.println(" [3] BATCH_STEP_EXECUTION - Step 실행 결과 (핵심)");
        System.out.println("----------------------------------------------------");
        List<Map<String, Object>> stepExecutions = jdbcTemplate.queryForList("""
                SELECT STEP_NAME, STATUS, EXIT_CODE,
                       READ_COUNT, WRITE_COUNT,
                       FILTER_COUNT,
                       READ_SKIP_COUNT, PROCESS_SKIP_COUNT, WRITE_SKIP_COUNT,
                       COMMIT_COUNT, ROLLBACK_COUNT
                FROM BATCH_STEP_EXECUTION
                ORDER BY STEP_EXECUTION_ID DESC LIMIT 1
                """);
        stepExecutions.forEach(row -> {
            System.out.println("  STEP_NAME         : " + row.get("STEP_NAME"));
            System.out.println("  STATUS            : " + row.get("STATUS"));
            System.out.println("  EXIT_CODE         : " + row.get("EXIT_CODE"));
            System.out.println("  ─────────────────────────────");
            System.out.println("  READ_COUNT        : " + row.get("READ_COUNT")    + "  ← Reader가 읽은 총 건수");
            System.out.println("  WRITE_COUNT       : " + row.get("WRITE_COUNT")   + "  ← Writer가 저장한 건수");
            System.out.println("  FILTER_COUNT      : " + row.get("FILTER_COUNT")  + "  ← Processor가 null 반환한 건수 (필터링)");
            System.out.println("  PROCESS_SKIP_COUNT: " + row.get("PROCESS_SKIP_COUNT") + "  ← Processor에서 Skip된 건수");
            System.out.println("  READ_SKIP_COUNT   : " + row.get("READ_SKIP_COUNT")    + "  ← Reader에서 Skip된 건수");
            System.out.println("  WRITE_SKIP_COUNT  : " + row.get("WRITE_SKIP_COUNT")   + "  ← Writer에서 Skip된 건수");
            System.out.println("  COMMIT_COUNT      : " + row.get("COMMIT_COUNT")   + "  ← 트랜잭션 커밋 횟수 (청크 수 + 1)");
            System.out.println("  ROLLBACK_COUNT    : " + row.get("ROLLBACK_COUNT") + "  ← 롤백 횟수");
        });

        // ================================================================
        // 4. BATCH_JOB_EXECUTION_PARAMS - Job 실행 시 전달된 파라미터
        // ================================================================
        System.out.println("\n----------------------------------------------------");
        System.out.println(" [4] BATCH_JOB_EXECUTION_PARAMS - 실행 파라미터");
        System.out.println("----------------------------------------------------");
        List<Map<String, Object>> params = jdbcTemplate.queryForList("""
                SELECT PARAMETER_NAME, PARAMETER_TYPE, PARAMETER_VALUE
                FROM BATCH_JOB_EXECUTION_PARAMS
                ORDER BY JOB_EXECUTION_ID DESC
                """);
        if (params.isEmpty()) {
            System.out.println("  (파라미터 없음 - 2강에서 다룹니다)");
        } else {
            params.forEach(row ->
                    System.out.println("  " + row.get("PARAMETER_NAME") + " = " + row.get("PARAMETER_VALUE")));
        }
        System.out.println("====================================================\n");
    }
}
