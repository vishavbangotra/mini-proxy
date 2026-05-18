package com.vishav.miniproxy.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.vishav.miniproxy.entity.BackendServer;
import com.vishav.miniproxy.loadbalancer.LoadBalancer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

@Component
public class ProxyServer {
    private static final Logger log = LoggerFactory.getLogger(ProxyServer.class);
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "transfer-encoding", "keep-alive",
            "proxy-authenticate", "proxy-authorization", "te", "trailers", "upgrade", "host"
    );
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final LoadBalancer loadBalancer;
    private HttpServer server;

    public ProxyServer(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        // 1. Build upstream request from the incoming one
        BackendServer backend = loadBalancer.getNextServer();
        URI upstream = URI.create("http://" + backend.getHost() + ":" + backend.getPort() + exchange.getRequestURI());
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(upstream)
                .method(exchange.getRequestMethod(),
                        HttpRequest.BodyPublishers.ofInputStream(exchange::getRequestBody))
                .timeout(Duration.ofSeconds(30));

        // Copy incoming headers, skipping hop-by-hop headers
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                values.forEach(v -> reqBuilder.header(name, v));
            }
        });

        HttpRequest req = reqBuilder.build();

        log.info("curl -X {} '{}'", req.method(), req.uri());

        // 2. Call downstream
        HttpResponse<InputStream> res;
        try {
            res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling upstream", e);
        }
        // 3. Copy status + headers + body back to caller
        res.headers().map().forEach((k, vs) -> vs.forEach(v -> exchange.getResponseHeaders().add(k, v)));
        exchange.sendResponseHeaders(res.statusCode(), -1);
        try (var out = exchange.getResponseBody(); var in = res.body()) {
            in.transferTo(out);
        }
    }

}
