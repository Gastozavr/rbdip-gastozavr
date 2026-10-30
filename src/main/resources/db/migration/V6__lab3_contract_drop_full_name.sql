-- ЛР3, шаг 3/3 (contract): накатывается только после того, как все
-- экземпляры приложения читают и пишут first_name/last_name. Старая
-- колонка full_name и триггер двойной записи больше не нужны.

DROP TRIGGER trg_lab3_sync_customer_names ON customers;
DROP FUNCTION lab3_sync_customer_names();

ALTER TABLE customers
    ALTER COLUMN first_name SET NOT NULL,
    ALTER COLUMN last_name SET NOT NULL,
    DROP COLUMN full_name;

DROP FUNCTION lab3_compose_full_name(TEXT, TEXT);
DROP FUNCTION lab3_first_name(TEXT);
DROP FUNCTION lab3_last_name(TEXT);
