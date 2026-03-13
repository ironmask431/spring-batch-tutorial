package com.batch.tutorial.job.customer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 고객 정제 배치 Job 통합 테스트
 *
 * ✅ @SpringBatchTest: JobLauncherTestUtils, JobRepositoryTestUtils 자동 주입
 * ✅ @SpringBootTest: 전체 Spring Context 로딩
 * ✅ @ActiveProfiles("test"): application-test.yml 사용
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class CustomerCleansingJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private Job customerCleansingJob; // 테스트할 Job 주입

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // ✅ 각 테스트 전 데이터 초기화 (테스트 독립성 보장)
        jdbcTemplate.update("DELETE FROM customers_processed");
        jdbcTemplate.update("UPDATE customers SET status = 'PENDING'");
        // Spring Batch 메타 데이터 초기화
        jobRepositoryTestUtils.removeJobExecutions();

        // 테스트할 Job 명시 (Job이 여러 개일 때 필요)
        jobLauncherTestUtils.setJob(customerCleansingJob);
    }

    @Test
    @DisplayName("정상 데이터 8건 처리 완료, 오류 데이터 2건 Skip")
    void customerCleansingJob_정상실행() throws Exception {
        // when: Job 실행
        JobExecution jobExecution = jobLauncherTestUtils.launchJob();

        // then: Job 완료 상태 확인
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // then: 처리된 건수 확인 (정상 8건)
        Integer processedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers_processed", Integer.class);
        assertThat(processedCount).isEqualTo(8);

        // then: PROCESSED 상태 확인
        Integer processedStatusCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers WHERE status = 'PROCESSED'", Integer.class);
        assertThat(processedStatusCount).isEqualTo(8);

        // then: SKIPPED 상태 확인 (오류 2건)
        Integer skippedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers WHERE status = 'SKIPPED'", Integer.class);
        assertThat(skippedCount).isEqualTo(2);
    }

    @Test
    @DisplayName("이메일 정규화 검증 - 대문자 이메일이 소문자로 변환")
    void customerCleansingJob_이메일정규화() throws Exception {
        // when
        jobLauncherTestUtils.launchJob();

        // then: 홍길동의 이메일이 소문자로 변환되었는지 확인
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers_processed WHERE email = 'hong@example.com'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("이름 공백 제거 검증 - '  홍길동  ' → '홍길동'")
    void customerCleansingJob_이름공백제거() throws Exception {
        // when
        jobLauncherTestUtils.launchJob();

        // then: 이름 공백이 제거되었는지 확인
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers_processed WHERE name = '홍길동'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("전화번호 정규화 검증 - '011 9876 5432' → '01198765432'")
    void customerCleansingJob_전화번호정규화() throws Exception {
        // when
        jobLauncherTestUtils.launchJob();

        // then: 전화번호 공백이 제거되었는지 확인
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers_processed WHERE phone = '01198765432'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Step 단위 테스트 - customerCleansingStep만 실행")
    void customerCleansingStep_단위테스트() throws Exception {
        // when: Step 단위 테스트 (Job 전체가 아닌 특정 Step만 실행 가능)
        JobExecution jobExecution = jobLauncherTestUtils.launchStep("customerCleansingStep");

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    /**
     * 핵심 검증:
     * 2번째 청크 = [조예린(6), 윤서준(7), 임지아(8), 이영희(9:오류), 박민준(10:오류)]
     * → 이영희, 박민준이 Skip 되어도 같은 청크의 조예린, 윤서준, 임지아는 정상 처리되어야 함
     */
    @Test
    @DisplayName("같은 청크 내 오류 건이 Skip 되어도 나머지 건은 정상 처리됨")
    void 같은청크_오류건_Skip_나머지_정상처리() throws Exception {
        // when
        jobLauncherTestUtils.launchJob();

        // then: 오류 데이터와 같은 청크(2번째)에 있던 조예린, 윤서준, 임지아가 정상 처리됐는지 확인
        Integer countInSameChunk = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers_processed WHERE customer_id IN (6, 7, 8)",
                Integer.class);
        assertThat(countInSameChunk)
                .as("오류 건(id=9,10)과 같은 청크였던 3건이 정상 저장되어야 함")
                .isEqualTo(3);

        // then: 오류 건(이영희, 박민준)은 customers_processed에 저장되지 않음
        Integer countOfSkipped = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers_processed WHERE customer_id IN (9, 10)",
                Integer.class);
        assertThat(countOfSkipped)
                .as("Skip된 건은 결과 테이블에 저장되면 안 됨")
                .isEqualTo(0);

        // then: Step 실행 통계로 최종 확인
        JobExecution jobExecution = jobLauncherTestUtils.launchJob();
        // 이미 PROCESSED 상태여서 Reader가 읽을 게 없음 → READ=0, WRITE=0, SKIP=0
        jobExecution.getStepExecutions().forEach(step -> {
            System.out.println("=== Step 실행 통계 ===");
            System.out.println("Read  : " + step.getReadCount());
            System.out.println("Write : " + step.getWriteCount());
            System.out.println("Skip  : " + step.getSkipCount());
            System.out.println("Commit: " + step.getCommitCount());
        });
    }
}
