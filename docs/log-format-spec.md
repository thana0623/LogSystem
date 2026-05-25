# Log Format Specification

## Standard JSON Format

Applications should output logs in the following JSON format (one line per log entry):

```json
{
  "timestamp": "2026-05-24T12:00:00.123Z",
  "level": "ERROR",
  "logger": "com.example.OrderService",
  "message": "Failed to create order: insufficient stock",
  "service_name": "order-service",
  "service_instance": "order-service-7d8f9-abc12",
  "trace_id": "a1b2c3d4e5f67890",
  "span_id": "1234567890abcdef",
  "exception": {
    "type": "java.lang.IllegalStateException",
    "message": "insufficient stock for item 42",
    "stacktrace": "..."
  },
  "tags": {
    "order_id": "ORD-12345",
    "user_id": "U-678"
  },
  "fields": {
    "duration_ms": 1450,
    "db_query": "SELECT ..."
  }
}
```

## Required Fields

| Field | Type | Description |
|-------|------|-------------|
| `timestamp` | string | ISO 8601 UTC format |
| `level` | string | TRACE / DEBUG / INFO / WARN / ERROR / FATAL |
| `message` | string | Log body |
| `service_name` | string | Service identifier |

## Optional Fields

| Field | Type | Description |
|-------|------|-------------|
| `service_instance` | string | Instance identifier |
| `logger` | string | Logger name |
| `trace_id` | string | 32-char hex trace ID |
| `span_id` | string | 16-char hex span ID |
| `exception` | object | Exception details |
| `tags` | object | Business key-value pairs |
| `fields` | object | Metric key-value pairs |

## Non-JSON Logs

Non-JSON logs are supported via Vector regex parsing, but will lose `tags`, `fields`, and `exception` structured data.
