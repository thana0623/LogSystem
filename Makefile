.PHONY: up down logs reset build clean dev dev-api dev-ui

# ─── Docker (生产/集成) ──────────────────────────────────────

# Start all services
up:
	docker compose up -d

# Stop all services
down:
	docker compose down

# Follow logs
logs:
	docker compose logs -f

# Reset everything (volumes included)
reset:
	docker compose down -v
	docker compose up -d

# ─── 开发环境 ─────────────────────────────────────────────────

# 启动前后端开发服务器
dev:
	powershell -ExecutionPolicy Bypass -File start-dev.ps1

# 仅启动后端
dev-api:
	powershell -ExecutionPolicy Bypass -File start-backend.ps1

# 仅启动前端
dev-ui:
	powershell -ExecutionPolicy Bypass -File start-frontend.ps1

# ─── 构建 ─────────────────────────────────────────────────────

# Build backend
build:
	cd logsys-api && mvn clean package -DskipTests

# Build frontend
build-ui:
	cd logsys-ui && npm run build

# Clean build artifacts
clean:
	cd logsys-api && mvn clean
