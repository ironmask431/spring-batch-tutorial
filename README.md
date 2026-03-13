# Spring Batch Tutorial

Spring Batch를 활용한 고객 데이터 정제(Customer Data Cleansing) 배치 프로세스 구현 튜토리얼입니다.

---

## 기술 스택

| 항목 | 버전 |
|---|---|
| Java | 17 |
| Spring Boot | 3.2.3 |
| Spring Batch | 5.x (Boot 내장) |
| Database | PostgreSQL |
| ORM | Spring Data JPA |
| Build | Gradle |

---

## 실행 환경

### 사전 조건
- Java 17 이상
- PostgreSQL 실행 중

### DB 설정

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/batch_tutorial
    username: root
    password: 1234
```

PostgreSQL에 `batch_tutorial` 데이터베이스를 미리 생성해야 합니다.

```sql
CREATE DATABASE batch_tutorial;
```

테이블 생성 및 샘플 데이터는 애플리케이션 시작 시 `schema.sql`, `data.sql`이 자동 실행됩니다.

### 실행

```bash
./gradlew bootJar
java -jar build/libs/spring-batch-tutorial.jar \
     --spring.batch.job.names=customerCleansingJob \
     --run.date=$(date +%Y%m%d%H%M%S)
```

> `spring.batch.job.enabled=false` 설정으로 자동 실행이 비활성화되어 있습니다.
> 외부 스케줄러(Jenkins, GitLab CI 등)에서 파라미터를 전달하여 실행합니다.

---

## 프로젝트 구조

```
src/main/java/com/batch/tutorial/
├── TutorialApplication.java
├── common/
│   ├── exception/
│   │   └── InvalidCustomerException.java   # 유효성 오류 시 Skip 처리용 예외
│   └── listener/
│       └── JobLoggingListener.java          # Job 시작/종료 로깅
└── job/customer/
    ├── CustomerCleansingJobConfig.java       # Job / Step / Reader 설정
    ├── domain/
    │   ├── Customer.java                     # 원본 고객 엔티티 (INPUT)
    │   ├── CustomerProcessed.java            # 정제된 고객 DTO (OUTPUT)
    │   └── CustomerStatus.java               # PENDING / PROCESSED / SKIPPED
    ├── processor/
    │   └── CustomerItemProcessor.java        # 유효성 검사 + 데이터 정규화
    ├── reader/
    │   └── CustomerRowMapper.java            # ResultSet → Customer 변환
    ├── writer/
    │   └── CustomerItemWriter.java           # DB 저장 + 상태 업데이트
    └── listener/
        └── CustomerSkipListener.java         # Skip 발생 시 상태 업데이트
```

---

## 배치 프로세스 동작

### 전체 흐름

```
[customers 테이블]          [Processor]          [customers_processed 테이블]
  status = PENDING  →  유효성 검사 + 정규화  →  정제된 데이터 INSERT
                              │
                        유효하지 않은 경우
                              ↓
                     status = SKIPPED 업데이트
```

Chunk Size = **5** 로 설정되어 있어 5건씩 읽고 처리하고 씁니다.

---

### 1단계 - Read

`JdbcCursorItemReader`가 `customers` 테이블에서 `status = 'PENDING'` 인 레코드를 `id ASC` 순으로 읽습니다.
페이지 방식이 아닌 DB 커서 방식을 사용하여 대용량 처리 시 데이터 누락을 방지합니다.

```sql
SELECT id, name, email, phone, age, status, created_at
FROM customers
WHERE status = 'PENDING'
ORDER BY id ASC
```

---

### 2단계 - Process

`CustomerItemProcessor`가 4가지 규칙으로 데이터를 검증 및 정규화합니다.

| 필드 | 처리 규칙 | 오류 시 |
|---|---|---|
| 이름 | `trim()` 공백 제거, 빈값이면 예외 | Skip |
| 이메일 | 소문자 변환 + 정규식 검증 | Skip |
| 전화번호 | 숫자 외 문자 전부 제거 | 정규화 |
| 나이 | 음수이면 예외 | Skip |

**샘플 데이터 정규화 예시**

| 원본 | 정제 결과 |
|---|---|
| `'  홍길동  '` | `'홍길동'` |
| `'hong@EXAMPLE.COM'` | `'hong@example.com'` |
| `'010-1234-5678'` | `'01012345678'` |
| `'INVALID_EMAIL'` | Skip 처리 |
| `age = -5` | Skip 처리 |

---

### 3단계 - Write

5건이 모이면 하나의 트랜잭션으로 두 가지 작업을 수행합니다.

**① `customers_processed` 테이블에 일괄 INSERT** (`JdbcTemplate.batchUpdate`)

```sql
INSERT INTO customers_processed (customer_id, name, email, phone, age, processed_at)
VALUES (?, ?, ?, ?, ?, ?)
```

**② 원본 `customers` 상태 UPDATE**

```sql
UPDATE customers SET status = 'PROCESSED' WHERE id IN (:ids)
```

---

### Skip 처리

`InvalidCustomerException` 발생 시 Job을 중단하지 않고 해당 건만 건너뜁니다.
`CustomerSkipListener`가 Skip된 레코드의 상태를 `SKIPPED`로 업데이트하고 경고 로그를 남깁니다.

- Skip 허용 한도: **100건** (초과 시 Job 전체 실패)

---

### 실행 결과 (샘플 데이터 10건 기준)

```
customers 테이블
  8건 → status = PROCESSED
  2건 → status = SKIPPED (이메일 오류 1건, 나이 음수 1건)

customers_processed 테이블
  8건 INSERT (정제된 데이터)
```

---

## Spring Batch 메타 테이블

Spring Batch가 자동 생성하는 Job 실행 이력 관리 테이블입니다.

### 테이블 관계도

```
batch_job_instance (1)
    └── batch_job_execution (N)
            ├── batch_job_execution_params (N)
            ├── batch_job_execution_context (1)
            └── batch_step_execution (N)
                    └── batch_step_execution_context (1)
```

### 테이블별 역할

#### batch_job_instance
Job의 논리적 실행 단위. Job 이름 + 파라미터 해시(JOB_KEY)로 중복 실행을 방지합니다.
동일 파라미터로 이미 성공한 Job은 재실행이 불가합니다.

#### batch_job_execution
Job의 실제 실행 내역. 실행 상태(`COMPLETED`, `FAILED`), 시작/종료 시각, 오류 메시지를 기록합니다.
Job 실패 시 같은 Instance에서 재실행하면 새로운 Execution이 추가됩니다.

#### batch_job_execution_params
Job 실행 시 전달된 파라미터 목록. `IDENTIFYING=true` 인 파라미터가 JOB_KEY 생성에 사용됩니다.

#### batch_job_execution_context
Job 레벨의 상태 저장소. 장애 복구(Restart) 시 이전 실행 상태를 복원하는 데 사용됩니다.

#### batch_step_execution
Step의 상세 실행 통계를 기록합니다. 운영 모니터링에 가장 유용한 테이블입니다.

| 컬럼 | 설명 |
|---|---|
| READ_COUNT | Reader가 읽은 건수 |
| WRITE_COUNT | Writer가 쓴 건수 |
| COMMIT_COUNT | 커밋 횟수 (= 청크 수) |
| PROCESS_SKIP_COUNT | Process 단계 Skip 건수 |
| ROLLBACK_COUNT | 롤백 횟수 |

이 프로젝트 실행 후 예상값: `READ_COUNT=10`, `WRITE_COUNT=8`, `PROCESS_SKIP_COUNT=2`

#### batch_step_execution_context
Step 레벨의 상태 저장소. Reader의 현재 커서 위치를 저장하여 장애 발생 시 이어서 처리할 수 있게 합니다.

---

## 테스트

```bash
./gradlew test
```

| 테스트 클래스 | 설명 |
|---|---|
| `BatchMetaTableDescribeTest` | Spring Batch 메타 테이블 구조 확인 |
| `BatchMetaDataQueryTest` | 메타 데이터 쿼리 테스트 |
| `CustomerCleansingJobTest` | 전체 Job 통합 테스트 |
