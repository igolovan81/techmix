CREATE KEYSPACE IF NOT EXISTS ecommerce
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 3};

USE ecommerce;

CREATE TABLE IF NOT EXISTS products (
    id uuid PRIMARY KEY,
    name text,
    price decimal,
    stock int
);

CREATE TABLE IF NOT EXISTS orders_by_customer (
    customer_id text,
    order_id timeuuid,
    product_id uuid,
    quantity int,
    unit_price decimal,
    line_total decimal,
    PRIMARY KEY (customer_id, order_id)
) WITH CLUSTERING ORDER BY (order_id DESC);

CREATE TABLE IF NOT EXISTS orders_by_product (
    product_id uuid,
    order_id timeuuid,
    customer_id text,
    quantity int,
    unit_price decimal,
    line_total decimal,
    PRIMARY KEY (product_id, order_id)
) WITH CLUSTERING ORDER BY (order_id DESC);

CREATE TABLE IF NOT EXISTS recently_viewed (
    product_id uuid,
    viewed_at timeuuid,
    PRIMARY KEY (product_id, viewed_at)
) WITH CLUSTERING ORDER BY (viewed_at DESC);

CREATE TABLE IF NOT EXISTS order_counts_by_product (
    product_id uuid PRIMARY KEY,
    order_count counter
);
