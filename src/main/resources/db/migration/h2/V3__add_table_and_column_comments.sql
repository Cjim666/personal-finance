COMMENT ON TABLE import_batch IS '账单导入批次，记录原始账单文件的解析、去重和确认状态';
COMMENT ON TABLE finance_transaction IS '已确认入账的正式交易记录';
COMMENT ON TABLE category IS '交易分类定义';
COMMENT ON TABLE category_rule IS '自动分类匹配规则';

COMMENT ON COLUMN import_batch.source IS '账单来源：ALIPAY=支付宝；WECHAT=微信支付；CMB=招商银行';
COMMENT ON COLUMN import_batch.status IS '导入状态：PENDING_CONFIRMATION=待确认；CONFIRMED=已确认入账；FAILED=解析或校验失败';
COMMENT ON COLUMN import_batch.file_hash IS '原始文件 SHA-256 哈希，用于文件级去重';
COMMENT ON COLUMN finance_transaction.source IS '账单来源：ALIPAY=支付宝；WECHAT=微信支付；CMB=招商银行';
COMMENT ON COLUMN finance_transaction.business_fingerprint IS '交易业务指纹，用于交易级去重';
COMMENT ON COLUMN finance_transaction.direction IS '交易方向：INCOME=收入；EXPENSE=支出；NEUTRAL=中性交易，不计入收支';
COMMENT ON COLUMN finance_transaction.amount IS '交易金额，单位为元；始终为非负数';
COMMENT ON COLUMN finance_transaction.category_origin IS '分类来源：AUTOMATIC=自动规则；MANUAL=人工指定；UNCLASSIFIED=未分类';
