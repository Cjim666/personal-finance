ALTER TABLE import_batch COMMENT = '账单导入批次，记录原始账单文件的解析、去重和确认状态';

ALTER TABLE import_batch
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY COLUMN source VARCHAR(32) NOT NULL COMMENT '账单来源：ALIPAY=支付宝；WECHAT=微信支付；CMB=招商银行',
    MODIFY COLUMN file_name VARCHAR(255) NOT NULL COMMENT '原始账单文件名',
    MODIFY COLUMN file_hash CHAR(64) NOT NULL COMMENT '原始文件 SHA-256 哈希，用于文件级去重',
    MODIFY COLUMN status VARCHAR(32) NOT NULL COMMENT '导入状态：PENDING_CONFIRMATION=待确认；CONFIRMED=已确认入账；FAILED=解析或校验失败',
    MODIFY COLUMN total_count INT NOT NULL DEFAULT 0 COMMENT '解析出的交易总数',
    MODIFY COLUMN duplicate_count INT NOT NULL DEFAULT 0 COMMENT '交易级去重命中的数量',
    MODIFY COLUMN error_message VARCHAR(1000) COMMENT '解析或校验失败原因，不记录敏感原始内容',
    MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '批次创建时间',
    MODIFY COLUMN confirmed_at DATETIME COMMENT '用户确认入账时间';

ALTER TABLE finance_transaction COMMENT = '已确认入账的正式交易记录';

ALTER TABLE finance_transaction
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY COLUMN import_batch_id BIGINT NOT NULL COMMENT '所属导入批次 ID',
    MODIFY COLUMN source VARCHAR(32) NOT NULL COMMENT '账单来源：ALIPAY=支付宝；WECHAT=微信支付；CMB=招商银行',
    MODIFY COLUMN external_id VARCHAR(128) COMMENT '来源系统交易单号；缺失时为空',
    MODIFY COLUMN business_fingerprint CHAR(64) NOT NULL COMMENT '交易业务指纹，用于交易级去重',
    MODIFY COLUMN occurred_at DATETIME NOT NULL COMMENT '交易发生时间；账单未提供时分秒时为当天 00:00:00',
    MODIFY COLUMN direction VARCHAR(16) NOT NULL COMMENT '交易方向：INCOME=收入；EXPENSE=支出；NEUTRAL=中性交易，不计入收支',
    MODIFY COLUMN amount DECIMAL(19,4) NOT NULL COMMENT '交易金额，单位为元；始终为非负数',
    MODIFY COLUMN account_name VARCHAR(255) COMMENT '账户名称或支付方式',
    MODIFY COLUMN counterparty VARCHAR(255) COMMENT '交易对方',
    MODIFY COLUMN summary VARCHAR(1000) COMMENT '交易摘要',
    MODIFY COLUMN category VARCHAR(100) COMMENT '分类名称，保留以兼容已有数据',
    MODIFY COLUMN raw_data JSON COMMENT '脱敏后的解析原始字段 JSON',
    MODIFY COLUMN created_at DATETIME NOT NULL COMMENT '正式交易创建时间';

ALTER TABLE category COMMENT = '交易分类定义';

ALTER TABLE category
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY COLUMN name VARCHAR(100) NOT NULL COMMENT '分类名称',
    MODIFY COLUMN active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用：TRUE=启用；FALSE=停用',
    MODIFY COLUMN sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序，数值越小越靠前';

ALTER TABLE category_rule COMMENT = '自动分类匹配规则';

ALTER TABLE category_rule
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY COLUMN category_id BIGINT NOT NULL COMMENT '命中的分类 ID',
    MODIFY COLUMN source VARCHAR(32) COMMENT '限定账单来源；为空时匹配全部来源',
    MODIFY COLUMN keyword VARCHAR(255) COMMENT '交易对方或摘要关键词；为空时不限制关键词',
    MODIFY COLUMN priority INT NOT NULL DEFAULT 0 COMMENT '规则优先级，数值越大越优先',
    MODIFY COLUMN active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用：TRUE=启用；FALSE=停用';

ALTER TABLE finance_transaction
    MODIFY COLUMN category_id BIGINT COMMENT '关联分类 ID；为空表示未分类',
    MODIFY COLUMN category_origin VARCHAR(16) NOT NULL DEFAULT 'UNCLASSIFIED' COMMENT '分类来源：AUTOMATIC=自动规则；MANUAL=人工指定；UNCLASSIFIED=未分类';
