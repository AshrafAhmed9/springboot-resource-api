package com.ashraf.notesapi.config;

import com.ashraf.notesapi.grpc.auth.AuthServiceGrpc;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class GrpcConfig {

    @Value("${grpc.auth.host:auth-api}")
    private String authServiceHost;

    @Value("${grpc.auth.port:9090}")
    private int authServicePort;

    @Bean
    @Lazy
    public ManagedChannel authServiceChannel() {
        return ManagedChannelBuilder
                .forAddress(authServiceHost, authServicePort)
                .usePlaintext()
                .build();
    }

    @Bean
    @Lazy
    public AuthServiceGrpc.AuthServiceBlockingStub authServiceStub(ManagedChannel authServiceChannel) {
        return AuthServiceGrpc.newBlockingStub(authServiceChannel);
    }
}
