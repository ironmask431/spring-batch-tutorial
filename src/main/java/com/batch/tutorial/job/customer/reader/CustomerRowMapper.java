package com.batch.tutorial.job.customer.reader;

import com.batch.tutorial.job.customer.domain.Customer;
import com.batch.tutorial.job.customer.domain.CustomerStatus;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * JDBC ResultSet → Customer 객체 변환
 */
public class CustomerRowMapper implements RowMapper<Customer> {

    @Override
    public Customer mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");

        return Customer.builder()
                .id(rs.getLong("id"))
                .name(rs.getString("name"))
                .email(rs.getString("email"))
                .phone(rs.getString("phone"))
                .age(rs.getObject("age") != null ? rs.getInt("age") : null)
                .status(CustomerStatus.valueOf(rs.getString("status")))
                .createdAt(createdAt != null ? createdAt.toLocalDateTime() : null)
                .build();
    }
}
