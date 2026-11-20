INSERT INTO category_rule (category_id, source, keyword, priority, active)
SELECT c.id, rules.source, rules.keyword, 100, TRUE
FROM category c
JOIN (VALUES
    ('交通', NULL, '铁路客票'),
    ('水电', NULL, '生活缴费'),
    ('通讯', NULL, '电信业务费')
) rules(category_name, source, keyword) ON c.name = rules.category_name;

UPDATE finance_transaction
SET direction = 'NEUTRAL'
WHERE direction = 'EXPENSE'
  AND (
      LOWER(COALESCE(counterparty, '') || ' ' || COALESCE(summary, '')) LIKE '%信用卡还款%'
      OR LOWER(COALESCE(counterparty, '') || ' ' || COALESCE(summary, '')) LIKE '%信用卡自动还款%'
      OR (source = 'ALIPAY'
          AND LOWER(COALESCE(counterparty, '') || ' ' || COALESCE(summary, '')) LIKE '%花呗%'
          AND LOWER(COALESCE(counterparty, '') || ' ' || COALESCE(summary, '')) LIKE '%还款%')
  );
