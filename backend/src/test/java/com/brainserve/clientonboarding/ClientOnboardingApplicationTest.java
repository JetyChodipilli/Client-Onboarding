package com.brainserve.clientonboarding;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ClientOnboardingApplicationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoadsWithMigratedDataSource() {
        assertThat(dataSource).isNotNull();
    }
}

