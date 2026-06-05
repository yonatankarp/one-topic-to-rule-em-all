.PHONY: help infra-up infra-down reset register unregister build test run-broken run-fixed scenario

# Default target: print help
help: ## Show this help message
	@awk '/^[a-zA-Z_-]+:.*?## / { \
		split($$0, a, ":.*?## "); \
		printf "  %-15s %s\n", a[1], a[2] \
	}' $(MAKEFILE_LIST)

# ---------------------------------------------------------------------------
# Infra
# ---------------------------------------------------------------------------

infra-up: ## Start Docker stack (Kafka + Schema Registry + Kafka UI) and wait for SR readiness
	docker compose up -d
	@echo "Waiting for schema registry..."
	@i=1; \
	while [ $$i -le 90 ]; do \
		if curl -sf http://localhost:8081/subjects > /dev/null 2>&1; then \
			echo "Schema registry is ready (attempt $$i)."; \
			exit 0; \
		fi; \
		if [ $$i -eq 90 ]; then \
			echo "ERROR: schema registry not ready after 90s" >&2; \
			exit 1; \
		fi; \
		sleep 1; \
		i=$$((i + 1)); \
	done

infra-down: ## Tear down Docker stack (removes volumes)
	docker compose down -v

reset: infra-down infra-up register ## Full rehearsal reset: tear down, start fresh, register schemas

# ---------------------------------------------------------------------------
# Schemas
# ---------------------------------------------------------------------------

register: ## Register all Avro schemas with Schema Registry
	./gradlew :schemas:registerAllSchemas

unregister: ## Unregister all Avro schemas from Schema Registry
	./gradlew :schemas:unregisterAllSchemas

# ---------------------------------------------------------------------------
# Code
# ---------------------------------------------------------------------------

build: ## Compile and assemble the project (skip tests)
	./gradlew build -x test

test: ## Run the full test suite
	./gradlew test

run-broken: ## Boot the app with the 'broken' Spring profile
	./gradlew :app:bootRun --args='--spring.profiles.active=broken'

run-fixed: ## Boot the app with the 'fixed' Spring profile
	./gradlew :app:bootRun --args='--spring.profiles.active=fixed'

scenario: ## Trigger the demo scenario (POST /scenario/run) and print JSON response
	@curl -s -X POST localhost:8080/scenario/run; echo
