package com.clearledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI clearLedgerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ClearLedger API")
                        .description("""
                                ClearLedger Phase 1 — Financial Ledger REST API.
                                Implements double-entry accounting, ACID transactions,
                                pessimistic concurrency control, and idempotent transfers.
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("ClearLedger Engineering")));
    }
}
