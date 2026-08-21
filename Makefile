SHELL := /bin/bash

.PHONY: help db-up db-down backend-test backend-run frontend-install frontend-test frontend-run verify

help:
	@echo "db-up             Start PostgreSQL"
	@echo "db-down           Stop local infrastructure"
	@echo "backend-test      Run backend verification"
	@echo "backend-run       Run Spring Boot locally"
	@echo "frontend-install  Install frontend dependencies"
	@echo "frontend-test     Run frontend lint, typecheck, and tests"
	@echo "frontend-run      Run Next.js dev server"
	@echo "verify            Run backend + frontend checks"

db-up:
	docker compose up -d postgres

db-down:
	docker compose down

backend-test:
	cd backend && mvn -B verify

backend-run:
	cd backend && mvn spring-boot:run

frontend-install:
	cd frontend && npm install --no-audit --no-fund

frontend-test:
	cd frontend && npm run lint && npm run typecheck && npm test -- --run && npm run build

frontend-run:
	cd frontend && npm run dev

verify: backend-test frontend-test
