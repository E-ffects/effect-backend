package com.u1_2.effect.meta.domain;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * v5 원본에서 접수 화면에 필요한 고객사와 제품 식별 정보만 읽음.
 */
@Repository
@RequiredArgsConstructor
public class MetaRepository {

    private final JdbcClient jdbcClient;

    public List<Customer> findCustomers() {
        return jdbcClient.sql("""
                SELECT customer_id, customer_name
                FROM pcb.customer
                ORDER BY customer_id
                """).query(Customer.class).list();
    }

    public List<Product> findProducts() {
        return jdbcClient.sql("""
                SELECT spec_id, part_no
                FROM pcb.product_spec
                ORDER BY spec_id
                """).query(Product.class).list();
    }

    public record Customer(String customerId, String customerName) {
    }

    public record Product(String specId, String partNo) {
    }
}
