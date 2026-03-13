package com.batch.tutorial;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Batch Tutorial Application
 *
 * ✅ 개발 표준 준수 사항:
 * - 배치 업무는 별도 Repository로 구성 (이 프로젝트)
 * - 앱 내 스케줄 기능 사용 불가 → spring.batch.job.enabled=false 설정
 * - 외부 스케줄러(Jenkins 등)에서 Job 실행
 */
@SpringBootApplication
public class TutorialApplication {

    public static void main(String[] args) {
        SpringApplication.run(TutorialApplication.class, args);
    }
}
