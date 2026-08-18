package com.clearledger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableSpringDataWebSupport
public class JpaConfig {

    /**
     * Provides a TransactionTemplate bean used by integration tests to
     * run imperative transaction blocks (e.g. seeding balance data).
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }
}
