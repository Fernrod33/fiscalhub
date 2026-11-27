package com.fiscalhub.backend.integracao;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/** Mailpit real (SMTP 1025, API 8025) para os testes de integração, com o caos ligado (recusa simulada). */
@TestConfiguration(proxyBeanMethods = false)
public class MailpitTestcontainer {

    @Bean
    GenericContainer<?> mailpitContainer() {
        return new GenericContainer<>("axllent/mailpit:v1.27.11").withExposedPorts(1025, 8025).withEnv("MP_ENABLE_CHAOS", "true")
                .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));
    }

    @Bean
    DynamicPropertyRegistrar mailpitPropriedades(GenericContainer<?> mailpitContainer) {
        return r -> {
            r.add("spring.mail.host", mailpitContainer::getHost);
            r.add("spring.mail.port", () -> mailpitContainer.getMappedPort(1025));
        };
    }

    @Bean
    Mailpit mailpit(GenericContainer<?> mailpitContainer) {
        return new Mailpit("http://" + mailpitContainer.getHost() + ":" + mailpitContainer.getMappedPort(8025));
    }
}
