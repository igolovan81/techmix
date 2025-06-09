package com.techmix;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TechMixApplicationTest {

    @Test
    void contextLoads() {
        // This test verifies that the Spring Boot application context loads successfully
    }

    @Test
    void applicationStarts() {
        // This test verifies that the main method can be called without errors
        // In a real application, you might want to test specific functionality
        TechMixApplication.main(new String[]{});
    }
}