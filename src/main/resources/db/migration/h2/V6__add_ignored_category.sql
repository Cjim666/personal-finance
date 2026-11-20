INSERT INTO category (name, active, sort_order)
SELECT '忽略', TRUE, 1000
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '忽略');
