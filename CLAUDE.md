# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
mvn clean package

# Run
mvn spring-boot:run
# or
java -jar target/mini-proxy-0.0.1-SNAPSHOT.jar

# Test
mvn test

# Run a single test class
mvn test -Dtest=MiniProxyApplicationTests
```

## Architecture

Mini-proxy is a lightweight HTTP reverse proxy and load balancer built with Spring Boot 4 / Java 24. It uses Java's built-in `com.sun.net.httpserver.HttpServer` directly rather than Spring MVC controllers — there are no `@RestController` classes.

**Request flow:**

```
Client → ProxyServer (port 8085)
           └─ LoadBalancer.getNextServer()  ← round-robin over upstream URIs
                └─ Java HttpClient → upstream backend
                     └─ response headers + body copied back to client
```

**Key components:**

| Class | Role |
|---|---|
| `ProxyServer` | Registers a catch-all `HttpContext` on the embedded `HttpServer`, forwards requests via `java.net.http.HttpClient`, logs in curl format |
| `LoadBalancer` | Health-aware round-robin (`AtomicInteger`) over `BackendServer` objects; skips unhealthy servers, falls back to full pool if all are down |
| `HealthChecker` | Polls each `BackendServer`'s `/health` endpoint every 5 s via `@PostConstruct`-started scheduler; tracks consecutive successes/failures and flips `healthy` flag |
| `BackendServer` | Lombok `@Data` entity holding host, port, and mutable health state shared between `LoadBalancer` and `HealthChecker` |

**Wiring notes:**
- Backends are configured in `MiniProxyApplication.loadBalancer()` — currently `localhost:9001–9003`. `LoadBalancer` holds the authoritative list; `HealthChecker` gets the same object references via `LoadBalancer.getServers()`.
- Spring WebFlux is on the classpath but unused; the server runs on port 8085 set in `MiniProxyApplication.main()`, not via `application.properties`.
