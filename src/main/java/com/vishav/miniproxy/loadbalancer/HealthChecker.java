package com.vishav.miniproxy.loadbalancer;

import com.vishav.miniproxy.entity.BackendServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class HealthChecker {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final List<BackendServer> serverList;
    private ScheduledExecutorService scheduler;

    public HealthChecker(LoadBalancer loadBalancer) {
        this.serverList = loadBalancer.getServers();
    }

    void checkAllServers() {
        for (BackendServer server : serverList) {
            checkServer(server);
        }
    }

    void checkServer (BackendServer server) {
        URI upstream = URI.create("http://" + server.getHost() + ":" + server.getPort() + "/health");
        HttpRequest request = HttpRequest.newBuilder()
                                .uri(upstream)
                                .timeout(Duration.ofSeconds(5))
                                .build();

        HttpResponse<Void> res;

        try {
            res = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                server.setHealthy(true);
                server.setConsecutiveSuccess(server.getConsecutiveSuccess() + 1);
                server.setConsecutiveFailures(0);
            } else {
                server.setHealthy(false);
                server.setConsecutiveFailures(server.getConsecutiveFailures() + 1);
                server.setConsecutiveSuccess(0);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.setHealthy(false);
            server.setConsecutiveFailures(server.getConsecutiveFailures() + 1);
            server.setConsecutiveSuccess(0);
        } catch (IOException e) {
            server.setHealthy(false);
            server.setConsecutiveFailures(server.getConsecutiveFailures() + 1);
            server.setConsecutiveSuccess(0);
        }
    }

    @PostConstruct
    void startScheduler() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::checkAllServers, 0, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stopScheduler() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

}
