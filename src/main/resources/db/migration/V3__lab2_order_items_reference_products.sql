-- ЛР2: нормализация order_items. Вместо копии названия и цены товара
-- позиция заказа ссылается на products по product_id.

ALTER TABLE order_items ADD COLUMN product_id BIGINT;

-- Товары, которых уже нет в каталоге, восстанавливаются из снимка в
-- позициях заказа, чтобы не потерять историю заказов.
INSERT INTO products (name, price)
SELECT oi.product_name, max(oi.product_price)
FROM order_items oi
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.name = oi.product_name)
GROUP BY oi.product_name;

UPDATE order_items oi
SET product_id = (SELECT min(p.id) FROM products p WHERE p.name = oi.product_name);

ALTER TABLE order_items
    ALTER COLUMN product_id SET NOT NULL,
    ADD CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_product_id ON order_items (product_id);

ALTER TABLE order_items
    DROP COLUMN product_name,
    DROP COLUMN product_price;
