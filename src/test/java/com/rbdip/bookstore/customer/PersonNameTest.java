package com.rbdip.bookstore.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PersonNameTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Ivan Petrov        | Ivan  | Petrov",
        "'  Ivan Petrov  '  | Ivan  | Petrov",
        "Ivan   Petrov      | Ivan  | Petrov",
        "Madonna            | Madonna | ''",
        "Anna Maria Sidorova | Anna | Maria Sidorova",
    })
    void splitsFirstWordAsFirstName(String fullName, String firstName, String lastName) {
        assertThat(PersonName.parse(fullName)).isEqualTo(new PersonName(firstName, lastName));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Ivan    | Petrov | Ivan Petrov",
        "Madonna | ''     | Madonna",
    })
    void composesFullName(String firstName, String lastName, String fullName) {
        assertThat(new PersonName(firstName, lastName).fullName()).isEqualTo(fullName);
    }
}
