-- 仅用于首次手动初始化 MySQL 8+ 数据库。
-- 与 Flyway 自动迁移二选一：执行本文件后，不要再让应用对同一数据库执行迁移。

CREATE DATABASE IF NOT EXISTS personal
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE personal;

CREATE TABLE import_batch (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    source VARCHAR(32) NOT NULL COMMENT '账单来源：WECHAT、ALIPAY、CMB',
    file_name VARCHAR(255) NOT NULL COMMENT '原始账单文件名',
    file_hash CHAR(64) NOT NULL COMMENT '原始文件 SHA-256 哈希，用于文件级去重',
    status VARCHAR(32) NOT NULL COMMENT '导入批次状态',
    total_count INT NOT NULL DEFAULT 0 COMMENT '解析出的交易总数',
    duplicate_count INT NOT NULL DEFAULT 0 COMMENT '去重命中的交易数量',
    error_message VARCHAR(1000) COMMENT '解析或校验失败原因',
    created_at DATETIME NOT NULL COMMENT '批次创建时间',
    confirmed_at DATETIME COMMENT '用户确认入账时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_import_batch_file_hash (file_hash)
) COMMENT='账单导入批次';

CREATE TABLE category (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    name VARCHAR(100) NOT NULL COMMENT '分类名称',
    active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '显示排序，数值越小越靠前',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (name)
) COMMENT='交易分类';

CREATE TABLE finance_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    import_batch_id BIGINT NOT NULL COMMENT '所属导入批次 ID',
    source VARCHAR(32) NOT NULL COMMENT '账单来源：WECHAT、ALIPAY、CMB',
    external_id VARCHAR(128) COMMENT '来源系统交易流水号',
    business_fingerprint CHAR(64) NOT NULL COMMENT '交易业务指纹，用于交易级去重',
    occurred_at DATETIME NOT NULL COMMENT '交易发生时间',
    direction VARCHAR(16) NOT NULL COMMENT '收支方向：INCOME、EXPENSE、NEUTRAL',
    amount DECIMAL(19,4) NOT NULL COMMENT '交易金额',
    account_name VARCHAR(255) COMMENT '付款或收款账户名称',
    counterparty VARCHAR(255) COMMENT '交易对方',
    summary VARCHAR(1000) COMMENT '交易摘要',
    category VARCHAR(100) COMMENT '兼容保留的分类名称',
    category_id BIGINT COMMENT '交易分类 ID',
    category_origin VARCHAR(16) NOT NULL DEFAULT 'UNCLASSIFIED' COMMENT '分类来源：MANUAL、RULE、UNCLASSIFIED',
    raw_data JSON COMMENT '脱敏前的原始解析数据',
    created_at DATETIME NOT NULL COMMENT '记录创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_transaction_source_external (source, external_id),
    UNIQUE KEY uk_transaction_fingerprint (business_fingerprint),
    KEY idx_transaction_occurred_at (occurred_at),
    KEY idx_transaction_direction_occurred_at (direction, occurred_at),
    CONSTRAINT fk_transaction_import_batch FOREIGN KEY (import_batch_id) REFERENCES import_batch (id),
    CONSTRAINT fk_transaction_category FOREIGN KEY (category_id) REFERENCES category (id)
) COMMENT='已确认入账的交易记录';

CREATE TABLE category_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_id BIGINT NOT NULL COMMENT '目标分类 ID',
    source VARCHAR(32) COMMENT '适用账单来源，为空时适用于全部来源',
    keyword VARCHAR(255) COMMENT '交易对方或摘要匹配关键词，为空时匹配全部交易',
    priority INT NOT NULL DEFAULT 0 COMMENT '规则优先级，数值越大越优先',
    active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    PRIMARY KEY (id),
    KEY idx_category_rule_active_priority (active, priority),
    CONSTRAINT fk_category_rule_category FOREIGN KEY (category_id) REFERENCES category (id)
) COMMENT='交易自动分类规则';

INSERT INTO category (name, active, sort_order) VALUES
    ('餐饮', TRUE, 10), ('交通', TRUE, 20), ('水电', TRUE, 30), ('网购', TRUE, 40),
    ('保险', TRUE, 50), ('医疗', TRUE, 60), ('娱乐', TRUE, 70), ('未分类', TRUE, 999);
