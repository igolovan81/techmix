CREATE OR REPLACE FUNCTION calculate_order_total(p_order_id BIGINT)
RETURNS DECIMAL(12,2) AS $$
    SELECT COALESCE(SUM(quantity * unit_price), 0)
    FROM order_item
    WHERE order_id = p_order_id;
$$ LANGUAGE sql;
