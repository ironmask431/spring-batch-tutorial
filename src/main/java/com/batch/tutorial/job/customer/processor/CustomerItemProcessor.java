package com.batch.tutorial.job.customer.processor;

import com.batch.tutorial.common.exception.InvalidCustomerException;
import com.batch.tutorial.job.customer.domain.Customer;
import com.batch.tutorial.job.customer.domain.CustomerProcessed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 고객 데이터 유효성 검사 및 변환 처리기
 *
 * [처리 규칙]
 * 1. 이름: 앞뒤 공백 제거
 * 2. 이메일: 소문자 변환 + 형식 유효성 검사 → 오류 시 InvalidCustomerException (Skip)
 * 3. 전화번호: 숫자만 추출 (하이픈, 공백 제거)
 * 4. 나이: 음수 불가 → 오류 시 InvalidCustomerException (Skip)
 *
 * ✅ ItemProcessor<INPUT, OUTPUT>
 *    - INPUT : Customer (원본)
 *    - OUTPUT: CustomerProcessed (정제본)
 *    - null 반환 시 해당 아이템을 Writer에 전달하지 않음 (필터링)
 */
@Slf4j
public class CustomerItemProcessor implements ItemProcessor<Customer, CustomerProcessed> {

    // 이메일 형식 정규식
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Override
    public CustomerProcessed process(Customer customer) throws Exception {
        log.debug("Processing customer: id={}, name={}", customer.getId(), customer.getName());

        // 1. 이름 공백 제거
        String name = customer.getName().trim();
        if (name.isBlank()) {
            throw new InvalidCustomerException(
                    "이름이 비어 있습니다. customerId=" + customer.getId());
        }

        // 2. 이메일 처리
        String email = processEmail(customer);

        // 3. 전화번호 정규화 (숫자만 추출)
        String phone = processPhone(customer.getPhone());

        // 4. 나이 유효성 검사
        validateAge(customer);

        return CustomerProcessed.builder()
                .customerId(customer.getId())
                .name(name)
                .email(email)
                .phone(phone)
                .age(customer.getAge())
                .processedAt(LocalDateTime.now())
                .build();
    }

    private String processEmail(Customer customer) throws InvalidCustomerException {
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            return null; // 이메일 없으면 null 허용
        }

        String email = customer.getEmail().toLowerCase().trim();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidCustomerException(
                    "이메일 형식이 올바르지 않습니다. customerId=" + customer.getId()
                    + ", email=" + customer.getEmail());
        }
        return email;
    }

    private String processPhone(String phone) {
        if (phone == null) return null;
        // 숫자가 아닌 모든 문자 제거 (하이픈, 공백, 괄호 등)
        return phone.replaceAll("[^0-9]", "");
    }

    private void validateAge(Customer customer) throws InvalidCustomerException {
        if (customer.getAge() != null && customer.getAge() < 0) {
            throw new InvalidCustomerException(
                    "나이가 유효하지 않습니다. customerId=" + customer.getId()
                    + ", age=" + customer.getAge());
        }
    }
}
