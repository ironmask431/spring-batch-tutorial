package com.batch.tutorial.common.exception;

/**
 * 고객 데이터 유효성 검사 실패 시 발생하는 예외
 *
 * ✅ 이 예외는 Step 설정에서 Skip 대상으로 등록됨
 *    → 발생하면 해당 건을 건너뛰고 다음 건을 처리
 */
public class InvalidCustomerException extends Exception {

    public InvalidCustomerException(String message) {
        super(message);
    }
}
