package com.batch.tutorial.job.customer.writer;

import com.batch.tutorial.job.customer.domain.CustomerProcessed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

/**
 * 정제된 고객 데이터를 DB에 저장하는 Writer
 *
 * [처리 순서]
 * 1. customers_processed 테이블에 청크 단위 일괄 삽입 (batchUpdate)
 * 2. 원본 customers 테이블의 status를 PROCESSED로 업데이트
 *
 * ✅ JdbcTemplate.batchUpdate() 사용 → JPA보다 대용량 처리에 유리
 */
@Slf4j
@RequiredArgsConstructor
public class CustomerItemWriter implements ItemWriter<CustomerProcessed> {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public void write(Chunk<? extends CustomerProcessed> chunk) throws Exception {
        List<? extends CustomerProcessed> items = chunk.getItems();

        if (items.isEmpty()) return;

        // 1. customers_processed 테이블에 일괄 삽입
        insertProcessedCustomers(items);

        // 2. 원본 customers 상태 업데이트
        updateCustomerStatus(items);

        log.debug("✅ {}건 처리 완료", items.size());
    }

    private void insertProcessedCustomers(List<? extends CustomerProcessed> items) {
        String sql = """
                INSERT INTO customers_processed (customer_id, name, email, phone, age, processed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(sql, items, items.size(), (ps, item) -> {
            ps.setLong(1, item.getCustomerId());
            ps.setString(2, item.getName());
            ps.setString(3, item.getEmail());
            ps.setString(4, item.getPhone());

            if (item.getAge() != null) {
                ps.setInt(5, item.getAge());
            } else {
                ps.setNull(5, Types.INTEGER);
            }

            ps.setTimestamp(6, Timestamp.valueOf(item.getProcessedAt()));
        });
    }

    private void updateCustomerStatus(List<? extends CustomerProcessed> items) {
        List<Long> customerIds = items.stream()
                .map(CustomerProcessed::getCustomerId)
                .toList();

        // NamedParameterJdbcTemplate의 IN 절 처리 - SQL Injection 안전
        namedParameterJdbcTemplate.update(
                "UPDATE customers SET status = 'PROCESSED' WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", customerIds)
        );
    }
}
