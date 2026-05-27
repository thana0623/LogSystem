.PHONY: up down logs reset build clean

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

# Build backend
build:
	cd logsys-api && mvn clean package -DskipTests

# Clean build artifacts
clean:
	cd logsys-api && mvn clean
