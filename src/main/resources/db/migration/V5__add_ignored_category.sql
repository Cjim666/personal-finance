INSERT INTO category (name, active, sort_order) VALUES ('忽略', TRUE, 1000)
ON DUPLICATE KEY UPDATE active = TRUE, sort_order = VALUES(sort_order);
