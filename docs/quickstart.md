# Quick Start

## Prerequisites

- Docker 24.0+
- Docker Compose v2.20+

## Steps

```bash
# Clone
git clone https://github.com/xxx/logsys.git
cd logsys

# Configure
cp .env.example .env
# Edit .env to set passwords

# Start
docker compose up -d

# Verify
curl http://localhost:8080/health
open http://localhost:3000  # Grafana
```

## Business App Integration (Java Example)

Add to `logback-spring.xml`:

```xml
<appender name="CONSOLE_JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
</appender>
```

Add dependency:

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.0</version>
</dependency>
```

Logs will be automatically collected by Vector.
