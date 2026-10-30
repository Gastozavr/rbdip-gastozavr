-- ЛР3, шаг 1/3 (expand): добавляем first_name/last_name рядом с full_name.
--
-- Колонки nullable и без DEFAULT - ADD COLUMN меняет только метаданные и
-- не переписывает таблицу. CREATE TRIGGER берёт SHARE ROW EXCLUSIVE и не
-- блокирует чтения, поэтому старая версия приложения продолжает работать.
--
-- Пока живут обе версии приложения, триггер реализует двойную запись:
-- старая версия пишет только full_name - триггер заполняет first/last_name;
-- новая пишет только first/last_name - триггер собирает full_name.

ALTER TABLE customers
    ADD COLUMN first_name VARCHAR(255),
    ADD COLUMN last_name VARCHAR(255);

-- Правило разбиения совпадает с PersonName.parse в приложении: первое
-- слово - имя, остальное - фамилия ('' если слово одно).
CREATE FUNCTION lab3_first_name(full_name TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE AS
$$ SELECT split_part(btrim(full_name), ' ', 1) $$;

CREATE FUNCTION lab3_last_name(full_name TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE AS
$$ SELECT btrim(substr(btrim(full_name), length(split_part(btrim(full_name), ' ', 1)) + 1)) $$;

CREATE FUNCTION lab3_compose_full_name(first_name TEXT, last_name TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE AS
$$ SELECT CASE WHEN coalesce(last_name, '') = '' THEN first_name ELSE first_name || ' ' || last_name END $$;

CREATE FUNCTION lab3_sync_customer_names() RETURNS TRIGGER
    LANGUAGE plpgsql AS
$$
DECLARE
    full_name_changed BOOLEAN;
    parts_changed BOOLEAN;
BEGIN
    IF TG_OP = 'INSERT' THEN
        full_name_changed := NEW.full_name IS NOT NULL;
        parts_changed := NEW.first_name IS NOT NULL;
    ELSE
        full_name_changed := NEW.full_name IS DISTINCT FROM OLD.full_name;
        parts_changed := NEW.first_name IS DISTINCT FROM OLD.first_name
            OR NEW.last_name IS DISTINCT FROM OLD.last_name;
    END IF;

    IF full_name_changed AND NOT parts_changed THEN
        -- писала старая версия приложения
        NEW.first_name := lab3_first_name(NEW.full_name);
        NEW.last_name := lab3_last_name(NEW.full_name);
    ELSIF parts_changed AND NOT full_name_changed
        AND (NEW.first_name, coalesce(NEW.last_name, ''))
            IS DISTINCT FROM (lab3_first_name(NEW.full_name), lab3_last_name(NEW.full_name)) THEN
        -- писала новая версия приложения (backfill сюда не попадает:
        -- он пишет ровно разбиение текущего full_name)
        NEW.last_name := coalesce(NEW.last_name, '');
        NEW.full_name := lab3_compose_full_name(NEW.first_name, NEW.last_name);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_lab3_sync_customer_names
    BEFORE INSERT OR UPDATE ON customers
    FOR EACH ROW
EXECUTE FUNCTION lab3_sync_customer_names();
