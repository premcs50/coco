package com.billngshop.BILLING.repository;

import com.billngshop.BILLING.entity.Customer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByNameContainingIgnoreCaseOrPhoneContainingIgnoreCase(String name, String phone);
    boolean existsByPhoneIgnoreCase(String phone);
    Optional<Customer> findByPhoneIgnoreCase(String phone);
}
