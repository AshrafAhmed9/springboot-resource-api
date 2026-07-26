package com.ashraf.notesapi.support;

import com.ashraf.notesapi.NoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@Import(GrpcTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    // Singleton container pattern: started once and NEVER stopped by JUnit.
    // The Spring test context is cached and shared across all integration test
    // classes, so a per-class @Container (torn down after each class) would
    // leave later classes pointing at a dead container -> connection refused.
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notes_test")
            .withUsername("test")
            .withPassword("test");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected FakeAuthService fakeAuthService;

    @Autowired
    protected NoteRepository noteRepository;

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    void resetState() {
        fakeAuthService.simulateOutage(false);
        noteRepository.deleteAll();
    }
}
