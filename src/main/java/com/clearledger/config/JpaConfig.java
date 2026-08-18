package com.clearledger.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableSpringDataWebSupport
public class JpaConfig {

    /**
     * Ensures any DataSource URL provided by platform environments (such as Render's
     * postgresql://user:pass@host:port/db connection strings) is properly converted
     * into a standard JDBC URL (jdbc:postgresql://host:port/db) and credentials are
     * set on DataSourceProperties so that the PostgreSQL JDBC driver and Flyway connect cleanly.
     */
    @Bean
    public static BeanPostProcessor dataSourcePropertiesPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof DataSourceProperties properties) {
                    String rawUrl = properties.getUrl();
                    if (rawUrl != null && (rawUrl.startsWith("postgresql://") || rawUrl.startsWith("postgres://") || rawUrl.contains("@"))) {
                        try {
                            String uriString = rawUrl.startsWith("jdbc:") ? rawUrl.substring(5) : rawUrl;
                            java.net.URI uri = java.net.URI.create(uriString);
                            String host = uri.getHost();
                            int port = uri.getPort();
                            String path = uri.getPath();
                            String userInfo = uri.getUserInfo();

                            if (host != null) {
                                String cleanJdbcUrl = "jdbc:postgresql://" + host + (port > 0 ? ":" + port : "") + (path != null ? path : "");
                                properties.setUrl(cleanJdbcUrl);
                            }

                            if (userInfo != null) {
                                String[] parts = userInfo.split(":", 2);
                                if (properties.getUsername() == null || properties.getUsername().isBlank() || "clearledger".equals(properties.getUsername())) {
                                    properties.setUsername(parts[0]);
                                }
                                if (parts.length > 1 && (properties.getPassword() == null || properties.getPassword().isBlank() || "clearledger".equals(properties.getPassword()))) {
                                    properties.setPassword(parts[1]);
                                }
                            }
                        } catch (Exception e) {
                            if (!rawUrl.startsWith("jdbc:")) {
                                properties.setUrl("jdbc:" + rawUrl);
                            }
                        }
                    }
                }
                return bean;
            }
        };
    }

    /**
     * Provides a TransactionTemplate bean used by integration tests to
     * run imperative transaction blocks (e.g. seeding balance data).
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }
}
