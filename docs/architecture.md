# LogSystem Architecture

## Overview

LogSystem is a lightweight, single-machine log collection and analysis platform designed for small teams and individual developers.

## Tech Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Log Collection | Vector | Docker stdout + file log collection, ETL |
| Log Storage | ClickHouse 24.x | Columnar storage, high-speed queries |
| Metadata | PostgreSQL 16 | Service registry, configuration |
| API | Spring Boot 3.x | REST API, query routing |
| Analysis | Python 3.12 | Error clustering, statistics (Phase 2) |
| Visualization | Grafana OSS | Pre-built dashboards |
| Frontend | React 18 + TypeScript (Phase 2) | Enterprise-grade UI |

## Data Flow

```
Business Apps → stdout/file → Vector → ClickHouse → Spring Boot API → Grafana/Frontend
                                                            ↓
                                                       PostgreSQL (metadata)
```

## Deployment

Single machine, Docker Compose, < 1GB memory.
```
