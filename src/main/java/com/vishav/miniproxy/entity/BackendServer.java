package com.vishav.miniproxy.entity;

import lombok.Data;

@Data
public class BackendServer {
    String host;
    int port;
    boolean healthy = true;
    int consecutiveFailures = 0;
    int consecutiveSuccess = 0;

    public static BackendServer create(String host, int port) {
        BackendServer backendServer = new BackendServer();
        backendServer.setHost(host);
        backendServer.setPort(port);
        return backendServer;
    }
}
