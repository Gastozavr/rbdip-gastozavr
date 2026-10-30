-- ЛР3, шаг 2/3 (backfill): заполняем first_name/last_name у клиентов,
-- созданных до expand-шага. Новые строки уже заполняет триггер из V4.
--
-- full_name не меняется, поэтому триггер не перезаписывает его. Чтения
-- full_name не блокируются (UPDATE берёт ROW EXCLUSIVE). На большой таблице
-- этот шаг стоило бы делать пачками по id, здесь объём учебный.

UPDATE customers
SET first_name = lab3_first_name(full_name),
    last_name = lab3_last_name(full_name)
WHERE first_name IS NULL
   OR last_name IS NULL;
