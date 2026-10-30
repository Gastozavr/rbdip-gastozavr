package com.rbdip.bookstore.customer;

/**
 * Имя клиента, разбитое на имя и фамилию. Правило разбиения совпадает с
 * функцией {@code lab3_split_full_name} из миграций ЛР3: первое слово -
 * имя, всё остальное - фамилия (пустая строка, если слово одно).
 */
public record PersonName(String firstName, String lastName) {

    public static PersonName parse(String fullName) {
        String trimmed = fullName.strip();
        int separator = trimmed.indexOf(' ');
        if (separator < 0) {
            return new PersonName(trimmed, "");
        }
        return new PersonName(trimmed.substring(0, separator), trimmed.substring(separator + 1).strip());
    }

    public String fullName() {
        return lastName.isEmpty() ? firstName : firstName + " " + lastName;
    }
}
