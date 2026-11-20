INSERT INTO category (name, active, sort_order) VALUES
    ('通讯', TRUE, 35),
    ('日用', TRUE, 45)
ON DUPLICATE KEY UPDATE active = VALUES(active), sort_order = VALUES(sort_order);

INSERT INTO category_rule (category_id, source, keyword, priority, active)
SELECT category.id, rule_definition.source, rule_definition.keyword, 100, TRUE
FROM category
JOIN (
    SELECT '交通' AS category_name, NULL AS source, '铁路客票' AS keyword UNION ALL
    SELECT '水电', NULL, '生活缴费' UNION ALL
    SELECT '通讯', NULL, '电信业务费'
) AS rule_definition ON category.name = rule_definition.category_name;

UPDATE finance_transaction
SET direction = 'NEUTRAL'
WHERE direction = 'EXPENSE'
  AND (
      (source = 'CMB' AND (
          LOWER(CONCAT_WS(' ', counterparty, summary)) LIKE '%信用卡自动还款%'
          OR LOWER(CONCAT_WS(' ', counterparty, summary)) LIKE '%支付宝信贷业务待还款账户%'
          OR LOWER(CONCAT_WS(' ', counterparty, summary)) LIKE '%银联快捷支付 还款%'
      ))
      OR (source = 'ALIPAY'
          AND LOWER(CONCAT_WS(' ', counterparty, summary)) LIKE '%花呗%'
          AND LOWER(CONCAT_WS(' ', counterparty, summary)) LIKE '%还款%')
      OR LOWER(CONCAT_WS(' ', counterparty, summary)) LIKE '%信用卡还款%'
  );

WITH ranked_match AS (
    SELECT t.id AS transaction_id,
           r.category_id,
           ROW_NUMBER() OVER (PARTITION BY t.id ORDER BY r.priority DESC, r.id ASC) AS rank_number
    FROM finance_transaction t
    JOIN import_batch b ON b.id = t.import_batch_id AND b.status = 'CONFIRMED'
    JOIN category_rule r ON r.active = TRUE
        AND (r.source IS NULL OR r.source = t.source)
        AND LOWER(CONCAT_WS(' ', t.counterparty, t.summary)) LIKE CONCAT('%', LOWER(r.keyword), '%')
    WHERE t.direction = 'EXPENSE'
      AND t.category_origin = 'UNCLASSIFIED'
)
UPDATE finance_transaction t
JOIN ranked_match matched ON matched.transaction_id = t.id AND matched.rank_number = 1
JOIN category c ON c.id = matched.category_id
SET t.category_id = c.id,
    t.category = c.name,
    t.category_origin = 'AUTOMATIC';
