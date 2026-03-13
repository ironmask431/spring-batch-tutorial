package com.batch.tutorial.job.customer;

import org.junit.jupiter.api.BeforeEach;
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

@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class BatchMetaTableDescribeTest {

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired private JobRepositoryTestUtils jobRepositoryTestUtils;
    @Autowired private Job customerCleansingJob;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM customers_processed");
        jdbcTemplate.update("UPDATE customers SET status = 'PENDING'");
        jobRepositoryTestUtils.removeJobExecutions();
        jobLauncherTestUtils.setJob(customerCleansingJob);
        // Job 실행 (실제 데이터 생성)
        jobLauncherTestUtils.launchJob();
    }

    @Test
    void 메타테이블_전체_컬럼_조회() {
        line("BATCH_JOB_INSTANCE");
        query("SELECT * FROM BATCH_JOB_INSTANCE ORDER BY JOB_INSTANCE_ID DESC LIMIT 3");

        line("BATCH_JOB_EXECUTION");
        query("SELECT JOB_EXECUTION_ID, JOB_INSTANCE_ID, CREATE_TIME, START_TIME, END_TIME, STATUS, EXIT_CODE, EXIT_MESSAGE, LAST_UPDATED FROM BATCH_JOB_EXECUTION ORDER BY JOB_EXECUTION_ID DESC LIMIT 3");

        line("BATCH_JOB_EXECUTION_PARAMS");
        query("SELECT * FROM BATCH_JOB_EXECUTION_PARAMS ORDER BY JOB_EXECUTION_ID DESC LIMIT 5");

        line("BATCH_JOB_EXECUTION_CONTEXT");
        query("SELECT JOB_EXECUTION_ID, SHORT_CONTEXT FROM BATCH_JOB_EXECUTION_CONTEXT ORDER BY JOB_EXECUTION_ID DESC LIMIT 2");

        line("BATCH_STEP_EXECUTION");
        query("""
            SELECT STEP_EXECUTION_ID, STEP_NAME, JOB_EXECUTION_ID,
                   START_TIME, END_TIME, STATUS,
                   COMMIT_COUNT, READ_COUNT, FILTER_COUNT,
                   WRITE_COUNT, READ_SKIP_COUNT, WRITE_SKIP_COUNT,
                   PROCESS_SKIP_COUNT, ROLLBACK_COUNT,
                   EXIT_CODE
            FROM BATCH_STEP_EXECUTION
            ORDER BY STEP_EXECUTION_ID DESC LIMIT 3
        """);

        line("BATCH_STEP_EXECUTION_CONTEXT");
        query("SELECT STEP_EXECUTION_ID, SHORT_CONTEXT FROM BATCH_STEP_EXECUTION_CONTEXT ORDER BY STEP_EXECUTION_ID DESC LIMIT 2");
    }

    private void query(String sql) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        if (rows.isEmpty()) {
            System.out.println("  (데이터 없음)");
            return;
        }
        rows.forEach(row -> {
            row.forEach((col, val) -> System.out.printf("  %-30s: %s%n", col, val));
            System.out.println("  " + "-".repeat(60));
        });
    }

    private void line(String title) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  " + title);
        System.out.println("=".repeat(70));
    }
}
