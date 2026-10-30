package com.rbdip.bookstore.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Нагрузочный тест ЛР3: пока накатываются expand/backfill-миграции
 * разбиения customers.full_name, параллельно работает "старая версия
 * приложения" - читает заказы с full_name клиента и создаёт новых клиентов
 * и заказы, зная только full_name. Ни один запрос не должен упасть, а все
 * клиенты, в том числе созданные во время миграции, должны получить
 * first_name/last_name. Затем проверяется "новая версия" (пишет только
 * first_name/last_name) и contract-шаг.
 *
 * <p>Spring-контекст не поднимается: Hibernate с ddl-auto=validate не
 * стартует на схеме до expand-шага, поэтому запросы старой версии
 * воспроизводятся напрямую через JDBC.
 */
@Testcontainers
class NameSplitMigrationLoadIntegrationTest {

    private static final String AFTER_LAB2 = "3";
    private static final String AFTER_BACKFILL = "5";
    private static final int READERS = 4;
    private static final int WRITERS = 2;
    private static final int SEEDED_CUSTOMERS = 200;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bookstore_load")
            .withUsername("bookstore")
            .withPassword("bookstore");

    private PGSimpleDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanSchema() {
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
    }

    @Test
    void oldAppKeepsWorkingWhileNamesAreSplit() throws Exception {
        migrateTo(AFTER_LAB2);
        long productId = jdbc.queryForObject(
                "INSERT INTO products (name, price) VALUES ('Clean Code', 35.00) RETURNING id", Long.class);
        for (int i = 0; i < SEEDED_CUSTOMERS; i++) {
            oldAppPlacesOrder("Seeded Customer" + i, productId);
        }
        oldAppPlacesOrder("Madonna", productId);

        List<Exception> errors = new CopyOnWriteArrayList<>();
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(READERS + WRITERS);
        for (int i = 0; i < READERS; i++) {
            pool.submit(loop(running, errors, () -> {
                oldAppListsOrders();
                reads.incrementAndGet();
            }));
        }
        for (int i = 0; i < WRITERS; i++) {
            pool.submit(loop(running, errors, () -> {
                oldAppPlacesOrder("Live Customer " + writes.incrementAndGet(), productId);
            }));
        }

        migrateTo(AFTER_BACKFILL);
        // Ещё немного работы старой версии уже на расширенной схеме.
        int writesAfterMigration = writes.get() + WRITERS;
        while (writes.get() < writesAfterMigration && errors.isEmpty()) {
            Thread.onSpinWait();
        }
        running.set(false);
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(errors).as("запросы старой версии во время expand/backfill").isEmpty();
        assertThat(reads.get()).isPositive();
        assertThat(writes.get()).isPositive();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM customers WHERE first_name IS NULL OR last_name IS NULL", Integer.class))
                .as("first_name/last_name заполнены у всех клиентов, включая созданных во время миграции")
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM customers"
                                + " WHERE full_name <> CASE WHEN last_name = '' THEN first_name"
                                + " ELSE first_name || ' ' || last_name END",
                        Integer.class))
                .as("first_name/last_name согласованы с full_name")
                .isZero();
        assertThat(nameOf("Madonna")).containsEntry("first_name", "Madonna").containsEntry("last_name", "");

        // Новая версия приложения пишет только новые колонки - старая всё ещё читает full_name.
        jdbc.update("INSERT INTO customers (first_name, last_name, address) VALUES ('Anna', 'Sidorova', 'SPb')");
        assertThat(nameOf("Anna Sidorova")).containsEntry("first_name", "Anna");
        jdbc.update("UPDATE customers SET last_name = 'Ivanova' WHERE first_name = 'Anna'");
        assertThat(nameOf("Anna Ivanova")).containsEntry("last_name", "Ivanova");

        migrateTo(null);
        assertThat(jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_name = 'customers'",
                        String.class))
                .contains("first_name", "last_name")
                .doesNotContain("full_name");
        jdbc.update("INSERT INTO customers (first_name, last_name) VALUES ('Petr', 'Petrov')");
    }

    private void migrateTo(String target) {
        var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        config.load().migrate();
    }

    /** Запрос старой версии GET /orders. */
    private void oldAppListsOrders() {
        jdbc.queryForList("SELECT o.id, c.full_name, oi.quantity FROM orders o"
                + " JOIN customers c ON c.id = o.customer_id"
                + " JOIN order_items oi ON oi.order_id = o.id"
                + " ORDER BY o.id DESC LIMIT 20");
    }

    /** Запросы старой версии POST /orders: о first_name/last_name она не знает. */
    private void oldAppPlacesOrder(String fullName, long productId) {
        Long customerId = jdbc.queryForObject(
                "INSERT INTO customers (full_name, address) VALUES (?, 'Moscow') RETURNING id", Long.class, fullName);
        Long orderId = jdbc.queryForObject(
                "INSERT INTO orders (customer_id, status) VALUES (?, 'new') RETURNING id", Long.class, customerId);
        jdbc.update("INSERT INTO order_items (order_id, product_id, quantity) VALUES (?, ?, 1)", orderId, productId);
    }

    private Map<String, Object> nameOf(String fullName) {
        return jdbc.queryForMap("SELECT first_name, last_name FROM customers WHERE full_name = ?", fullName);
    }

    private static Runnable loop(AtomicBoolean running, List<Exception> errors, Runnable action) {
        return () -> {
            while (running.get()) {
                try {
                    action.run();
                } catch (RuntimeException e) {
                    errors.add(e);
                }
            }
        };
    }
}
