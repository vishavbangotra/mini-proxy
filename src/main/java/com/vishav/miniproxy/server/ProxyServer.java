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

@Component
public class ProxyServer {
    private static final Logger log = LoggerFactory.getLogger(ProxyServer.class);
    private final HttpClient client = HttpClient.newHttpClient();
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
        HttpRequest req = HttpRequest.newBuilder(upstream)
                .method(exchange.getRequestMethod(),
                        HttpRequest.BodyPublishers.ofInputStream(exchange::getRequestBody))
                .build();

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
        exchange.sendResponseHeaders(res.statusCode(), 0);
        try (var out = exchange.getResponseBody(); var in = res.body()) {
            in.transferTo(out);
        }
    }

}
