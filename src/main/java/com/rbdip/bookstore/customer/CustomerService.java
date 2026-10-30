package com.rbdip.bookstore.customer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /** Повторный заказ с теми же контактами не плодит дубликат клиента. */
    @Transactional
    public Customer findOrCreate(String fullName, String address, String phone) {
        PersonName name = PersonName.parse(fullName);
        return customerRepository
                .findFirstByFirstNameAndLastNameAndAddressAndPhone(name.firstName(), name.lastName(), address, phone)
                .orElseGet(() -> customerRepository.save(new Customer(name, address, phone)));
    }
}
