package com.vishav.miniproxy;

import com.vishav.miniproxy.entity.BackendServer;
import com.vishav.miniproxy.loadbalancer.LoadBalancer;
import com.vishav.miniproxy.server.ProxyServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@SpringBootApplication
public class MiniProxyApplication {

	@Bean
	public LoadBalancer loadBalancer() {
		return new LoadBalancer(List.of(BackendServer.create("localhost", 9001),
				BackendServer.create("localhost", 9002),
				BackendServer.create("localhost", 9003)
		));
	}

	public static void main(String[] args) throws IOException {
		ConfigurableApplicationContext ctx = SpringApplication.run(MiniProxyApplication.class, args);
		ProxyServer ps = ctx.getBean(ProxyServer.class);
		ps.start(8085);
	}

}
