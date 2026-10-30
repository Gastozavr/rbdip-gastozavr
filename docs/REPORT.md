# Отчёт по лабораторным работам

Итог локального прогона пайплайна CI (`mvn verify` + PIT + `scripts/write_score.py`): **100/100**.

| Гейт | Результат |
|---|---|
| `lab1_style` | Checkstyle 0, PMD 10 (из 16 и 9 на старте), эталонные тесты зелёные |
| `lab2_coverage` | построчное покрытие 92% |
| `lab2_schema` | ✅ |
| `lab3_migration` | ✅ |
| `lab4_nplusone` | `GET /orders` — 1 SQL-запрос вместо 1 + N |
| `lab4_architecture` | ✅ |
| `lab5_mutation` | mutation score `PricingCalculator` — 100% (12/12) |
| `lab5_reference` | ✅ |

> В `scripts/write_score.py` PMD-нарушения ищутся в namespace `net.sourceforge.pmd`,
> а PMD 7 пишет отчёт в `http://pmd.sourceforge.net/report/2.0.0`, поэтому скрипт
> всегда видит `pmd=0`. Цифры PMD выше посчитаны по `target/pmd.xml` напрямую.

## ЛР1 — найденные запахи и что с ними сделано

| Где | Запах | Решение |
|---|---|---|
| `OrderService#createOrder` | God-метод: валидация, поиск товаров, расчёт цены, сохранение и «письмо» в одном методе (CC 16, NPath 540) | `OrderValidator` — валидация запроса; `CustomerService` — клиент; `PricingCalculator` — цена (теперь Spring-бин, а не `new`); `OrderNotifier`/`LoggingOrderNotifier` — уведомление; `OrderService` только оркестрирует |
| `OrderService#sendConfirmationEmail` | `System.out` вместо логгера, побочный эффект зашит в сервис | интерфейс `OrderNotifier`, реализация через SLF4J |
| `OrderService` | ручная синхронизация параллельных списков `products`/`lineItems` по индексу | позиции добавляются в агрегат `Order#addItem`, позиции для расчёта строятся из него |
| `OrderService` | значения по умолчанию (`"regular"`, количество 1) размазаны по коду | `CreateOrderRequest#customerTypeOrDefault`, `Item#quantityOrDefault` |
| `PricingCalculator` | magic numbers `10`, `0.95`, `0.9`, `0.85`, `0.8`, `1000`, `0.98`; цепочки `if/else` по строкам; `BigDecimal.valueOf(double)` | именованные константы, таблицы скидок по типу клиента и купонам, `BigDecimal` из строк; метод разбит на шаги с явным порядком применения скидок |
| `OrderController` | контроллер ходит в репозитории в обход сервиса, N+1 | только `OrderService`, ответ — типизированные `OrderSummary`/`ItemSummary` |
| `ReviewService` | прямая зависимость от репозиториев `order`; `findAll()` двух таблиц ради булева флага; неиспользуемая переменная | контракт `PurchaseVerifier` (ЛР4), флаг возвращается в ответе API |
| `OrderItemRepository` | `findByOrderId` существовал только ради N+1 | удалён |
| Схема БД | `orders` хранит контакты клиента, `order_items` — копию названия/цены товара, нет FK на `products`, нет индексов на FK | ЛР2 |

## ЛР2 — характеризационные тесты и нормализация

- `PricingCalculatorTest` — 29 характеризационных тестов: пороги оптовой скидки (10/11 шт.) и
  крупного заказа (1000 / 1000.01), все типы клиентов, включая неизвестный, `null` и регистр,
  оба купона, неизвестный купон, итог ниже нуля и ровно ноль, округление `HALF_UP`, порядок
  применения скидок. Тесты сначала прогнаны на **исходной** реализации, затем класс отрефакторен.
- `V2__lab2_extract_customers.sql` — таблица `customers (full_name, address, phone)`, перенос
  уникальных контактов из `orders`, `orders.customer_id` (FK + индекс), удаление старых колонок.
- `V3__lab2_order_items_reference_products.sql` — `order_items.product_id` (FK + индексы);
  товары, которых нет в каталоге, восстанавливаются из снимка в позиции заказа; удаление
  `product_name`/`product_price`.
- Повторный заказ с теми же контактами переиспользует строку клиента (`CustomerService#findOrCreate`).

## ЛР3 — expand-contract для `customers.full_name`

| Миграция | Шаг | Что делает |
|---|---|---|
| `V4__lab3_expand_add_name_columns.sql` | expand | nullable `first_name`/`last_name` и триггер двойной записи: старая версия пишет `full_name` — триггер заполняет новые колонки; новая пишет `first_name`/`last_name` — триггер собирает `full_name` |
| `V5__lab3_backfill_names.sql` | backfill | заполнение новых колонок у существующих клиентов, `full_name` не меняется |
| `V6__lab3_contract_drop_full_name.sql` | contract | удаление триггера и функций, `NOT NULL` на новые колонки, удаление `full_name` |

Правило разбиения одно и то же в SQL и в `PersonName.parse`: первое слово — имя, остальное —
фамилия (пустая строка для одного слова).

Собственный нагрузочный тест — `NameSplitMigrationLoadIntegrationTest`: на 200+ клиентах
4 потока читают заказы с `full_name`, а 2 потока создают клиентов и заказы так, как это делает
старая версия приложения, пока накатываются expand и backfill. Тест проверяет отсутствие ошибок,
заполненность и согласованность имён (включая клиентов, созданных во время миграции), запись
новой версией только в `first_name`/`last_name` и итоговую схему после contract.

## ЛР4 — N+1 и выделение модуля review

- `OrderRepository#findAllWithItems` — `join fetch` клиента, позиций и товаров: один SQL-запрос
  на `GET /orders` при любом числе заказов.
- Пакет `purchase` с интерфейсом `PurchaseVerifier` — единственная точка, через которую `review`
  узнаёт о покупках. Реализация `OrderPurchaseVerifier` и `OrderItemRepository` в `order` закрыты
  на уровне пакета. `POST /products/{id}/reviews` дополнительно возвращает `verifiedPurchase`.

## ЛР5 — финальный цикл

- Весь пайплайн проходит одновременно.
- Mutation score `PricingCalculator` — 100% только на тестах студента (эталонные исключены).
- В конфигурацию PIT добавлено исключение `*IntegrationTest`: интеграционные тесты поднимают
  Testcontainers и Spring-контекст, для мутационного прогона это слишком медленно, и на score они
  не влияют.
