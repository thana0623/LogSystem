-- ============================================================
-- ClickHouse init script — LogSystem
-- Idempotent: safe to run on every container start
-- ============================================================

CREATE TABLE IF NOT EXISTS logs (
    timestamp         DateTime64(3)   NOT NULL  COMMENT 'Log timestamp (business time)',
    date              Date            NOT NULL  COMMENT 'Partition key, materialized from timestamp',

    service_name      String          NOT NULL  COMMENT 'Service name, e.g. order-service',
    service_instance  String          DEFAULT '' COMMENT 'Instance identifier, e.g. Pod name',
    source_host       String          DEFAULT '' COMMENT 'Host machine name or IP',
    source_type       String          DEFAULT '' COMMENT 'Collection source: stdout | file | http',

    level             String          NOT NULL  COMMENT 'Log level: TRACE/DEBUG/INFO/WARN/ERROR/FATAL',
    logger            String          DEFAULT '' COMMENT 'Logger name or module name',
    message           String          NOT NULL  COMMENT 'Log body. Full-text search target',

    trace_id          String          DEFAULT '' COMMENT '32-char hex distributed trace ID',
    span_id           String          DEFAULT '' COMMENT '16-char hex Span ID',

    exception_type       String       DEFAULT '' COMMENT 'Exception class name',
    exception_message    String       DEFAULT '' COMMENT 'Exception message, normalized for clustering',
    exception_stacktrace String       DEFAULT '' COMMENT 'Full exception stacktrace, may be 10KB+',
    error_signature      FixedString(32) DEFAULT '' COMMENT 'Exception signature MD5 for clustering',

    tags         Map(String, String)  COMMENT 'Business tags: order_id, user_id, etc.',
    fields       Map(String, String)  COMMENT 'Structured metrics: duration_ms, db_rows, etc.',

    ingestion_time DateTime64(3) DEFAULT now() COMMENT 'Vector ingestion time'
)
ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(date)
ORDER BY (service_name, level, toUnixTimestamp(timestamp))
TTL date + INTERVAL 30 DAY DELETE
SETTINGS index_granularity = 8192;

-- Indexes
ALTER TABLE logs ADD INDEX IF NOT EXISTS idx_message message TYPE tokenbf_v1(32768, 3, 0) GRANULARITY 4;
ALTER TABLE logs ADD INDEX IF NOT EXISTS idx_trace_id trace_id TYPE bloom_filter() GRANULARITY 4;
ALTER TABLE logs ADD INDEX IF NOT EXISTS idx_error_signature error_signature TYPE bloom_filter() GRANULARITY 1;
ALTER TABLE logs ADD INDEX IF NOT EXISTS idx_exception_msg exception_message TYPE tokenbf_v1(32768, 3, 0) GRANULARITY 4;

-- Error clusters table (Phase 2)
CREATE TABLE IF NOT EXISTS error_clusters (
    signature         FixedString(32)  COMMENT 'Exception signature MD5',
    exception_type    String           COMMENT 'Exception class name',
    normalized_msg    String           COMMENT 'Normalized message template',
    sample_message    String           COMMENT 'One raw message sample',
    service_name      String           COMMENT 'Source service',
    first_seen        DateTime64(3)    COMMENT 'First occurrence time',
    last_seen         DateTime64(3)    COMMENT 'Last occurrence time',
    total_count       UInt64           COMMENT 'Total occurrence count (auto-summed by SummingMergeTree)',
    date              Date             COMMENT 'Partition key'
)
ENGINE = SummingMergeTree(total_count)
PARTITION BY toYYYYMM(date)
ORDER BY (date, service_name, signature)
TTL date + INTERVAL 90 DAY DELETE
SETTINGS index_granularity = 8192;
