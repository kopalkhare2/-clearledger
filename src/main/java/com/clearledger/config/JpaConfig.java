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
     * postgresql:// connection strings) is automatically prefixed with 'jdbc:'
     * so that the PostgreSQL JDBC driver and Flyway accept it.
     */
    @Bean
    public static BeanPostProcessor dataSourcePropertiesPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof DataSourceProperties properties) {
                    String url = properties.getUrl();
                    if (url != null && !url.startsWith("jdbc:")) {
                        properties.setUrl("jdbc:" + url);
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
