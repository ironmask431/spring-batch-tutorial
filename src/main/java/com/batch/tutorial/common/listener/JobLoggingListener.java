package com.batch.tutorial.common.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

import java.time.temporal.ChronoUnit;

/**
 * Job 시작/종료 시 로깅을 담당하는 리스너
 *
 * ✅ 개발 표준: 배치 업무 로깅 필수
 * - Job 시작 시간, 종료 시간, 실행 상태, 오류 메시지 기록
 */
@Slf4j
public class JobLoggingListener implements JobExecutionListener {

    private static final String DIVIDER = "=".repeat(60);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info(DIVIDER);
        log.info("▶ JOB START");
        log.info("  Job Name  : {}", jobExecution.getJobInstance().getJobName());
        log.info("  Job ID    : {}", jobExecution.getJobId());
        log.info("  Parameters: {}", jobExecution.getJobParameters());
        log.info(DIVIDER);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        // startTime이 null일 수 있으므로 방어 처리
        long durationMs = 0;
        if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
            durationMs = ChronoUnit.MILLIS.between(
                    jobExecution.getStartTime(), jobExecution.getEndTime());
        }

        log.info(DIVIDER);
        log.info("■ JOB END");
        log.info("  Job Name  : {}", jobExecution.getJobInstance().getJobName());
        log.info("  Status    : {}", jobExecution.getStatus());
        log.info("  Duration  : {}ms", durationMs);

        // Step별 처리 결과 요약 출력
        jobExecution.getStepExecutions().forEach(step -> {
            log.info("  [Step: {}]", step.getStepName());
            log.info("    Read    : {}", step.getReadCount());
            log.info("    Process : {}", step.getReadCount() - step.getSkipCount());
            log.info("    Write   : {}", step.getWriteCount());
            log.info("    Skip    : {}", step.getSkipCount());
        });

        // 실패 시 오류 상세 출력
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            log.error("  ❌ Job 실패 원인:");
            jobExecution.getAllFailureExceptions()
                    .forEach(e -> log.error("    - {}", e.getMessage()));
        }

        log.info(DIVIDER);
    }
}
