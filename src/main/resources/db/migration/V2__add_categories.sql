CREATE TABLE category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (name)
);

CREATE TABLE category_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    source VARCHAR(32),
    keyword VARCHAR(255),
    priority INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    KEY idx_category_rule_active_priority (active, priority),
    CONSTRAINT fk_category_rule_category FOREIGN KEY (category_id) REFERENCES category (id)
);

ALTER TABLE finance_transaction
    ADD COLUMN category_id BIGINT,
    ADD COLUMN category_origin VARCHAR(16) NOT NULL DEFAULT 'UNCLASSIFIED',
    ADD CONSTRAINT fk_transaction_category FOREIGN KEY (category_id) REFERENCES category (id);

INSERT INTO category (name, active, sort_order) VALUES
    ('餐饮', TRUE, 10), ('交通', TRUE, 20), ('水电', TRUE, 30), ('网购', TRUE, 40),
    ('保险', TRUE, 50), ('医疗', TRUE, 60), ('娱乐', TRUE, 70), ('未分类', TRUE, 999);

UPDATE finance_transaction transaction_record
JOIN category category_definition ON category_definition.name = transaction_record.category
SET transaction_record.category_id = category_definition.id,
    transaction_record.category_origin = 'UNCLASSIFIED';
