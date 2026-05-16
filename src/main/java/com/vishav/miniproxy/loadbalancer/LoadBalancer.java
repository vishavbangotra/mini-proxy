package com.vishav.miniproxy.loadbalancer;

import com.vishav.miniproxy.entity.BackendServer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalancer {
    private final List<BackendServer> servers;
    private final AtomicInteger counter = new AtomicInteger(0);

    public LoadBalancer(List<BackendServer> servers){
        if (servers.isEmpty()){
            throw new IllegalArgumentException("[ERROR] Need at least 1 server to initialise the Load Balancer");
        }
        this.servers = List.copyOf(servers);
    }

    public List<BackendServer> getServers() {
        return servers;
    }

    public BackendServer getNextServer(){
        List<BackendServer> pool = servers.stream().filter(BackendServer::isHealthy).toList();
        if (pool.isEmpty()) pool = servers; // fall back to all servers if none are healthy
        return pool.get(counter.getAndIncrement() % pool.size());
    }
}
