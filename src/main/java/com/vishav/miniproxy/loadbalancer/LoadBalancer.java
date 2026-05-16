package com.vishav.miniproxy.loadbalancer;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalancer {
    private final List<URI> servers;
    private final AtomicInteger counter = new AtomicInteger(0);

    public LoadBalancer(List<URI> servers){
        if (servers.isEmpty()){
            throw new IllegalArgumentException("[ERROR] Need at least 1 server to initialise the Load Balancer");
        }
        this.servers = List.copyOf(servers);
    }

    public URI getNextServer(){
        return servers.get(counter.getAndIncrement() % servers.size());
    }



}
