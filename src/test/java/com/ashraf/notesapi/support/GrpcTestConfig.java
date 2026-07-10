package com.ashraf.notesapi.support;

import com.ashraf.notesapi.grpc.auth.AuthServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.util.UUID;

/**
 * Replaces the real gRPC channel/stub beans with an in-process server backed
 * by {@link FakeAuthService}, so integration tests never need the Go binary.
 */
@TestConfiguration
public class GrpcTestConfig {

    private Server server;
    private ManagedChannel channel;

    @Bean
    @Primary
    public FakeAuthService fakeAuthService() {
        return new FakeAuthService();
    }

    @Bean
    @Primary
    public AuthServiceGrpc.AuthServiceBlockingStub authServiceStub(FakeAuthService fakeAuthService) throws IOException {
        String serverName = "test-server-" + UUID.randomUUID();

        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(fakeAuthService)
                .build()
                .start();

        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();

        return AuthServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }
}
