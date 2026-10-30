package com.rbdip.bookstore.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Клиент магазина. Выделен из orders в ЛР2; имя разбито на first_name /
 * last_name в ЛР3 (expand-contract над customers.full_name).
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String address;

    private String phone;

    protected Customer() {
        // for JPA
    }

    public Customer(PersonName name, String address, String phone) {
        this.firstName = name.firstName();
        this.lastName = name.lastName();
        this.address = address;
        this.phone = phone;
    }

    public Long getId() {
        return id;
    }

    public PersonName getName() {
        return new PersonName(firstName, lastName);
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }
}
