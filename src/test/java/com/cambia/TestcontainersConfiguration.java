package com.cambia;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	public PostgreSQLContainer postgresContainer() {
		// Mesma versão de produção (docker-compose.yml e README): com "latest" os testes
		// rodavam numa versão mais nova e uma diferença de comportamento passaria batida.
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16"));
	}

}
