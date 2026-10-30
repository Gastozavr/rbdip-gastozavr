package com.rbdip.bookstore.customer;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // null в address/phone Spring Data превращает в IS NULL.
    Optional<Customer> findFirstByFirstNameAndLastNameAndAddressAndPhone(
            String firstName, String lastName, String address, String phone);
}
