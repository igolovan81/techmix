package com.testingai.redis.config;

import io.lettuce.core.internal.HostAndPort;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.MappingSocketAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

@Configuration
public class LettuceConfig {

    // Cluster nodes announce themselves with the Docker bridge gateway IP (e.g. 172.20.0.1),
    // which is unreachable from the Mac host. Remap all connections to localhost:<same-port>
    // so Docker's port mappings are used instead.
    @Bean(destroyMethod = "shutdown")
    public ClientResources clientResources() {
        Function<HostAndPort, HostAndPort> mapper = hp -> HostAndPort.of("localhost", hp.getPort());
        MappingSocketAddressResolver resolver = MappingSocketAddressResolver.create(mapper);
        return DefaultClientResources.builder()
                .socketAddressResolver(resolver)
                .build();
    }
}
