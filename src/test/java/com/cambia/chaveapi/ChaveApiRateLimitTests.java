package com.cambia.chaveapi;

import com.cambia.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremento 79 (A10 de docs/chaves-api-plano.md): tentativas com chave inválida passam
 * por um limitador por IP próprio da chave de API (separado do login — ver
 * ChaveApiLimitadorIsoladoTests). Classe separada de ChaveApiAutenticacaoTests porque
 * precisa de um limite baixo, e a suíte inteira roda com o limite alto
 * (src/test/resources/application.properties).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "cambia.rate-limit.chave-api.maximo=5")
class ChaveApiRateLimitTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void tentativasInvalidasSaoLimitadas() throws Exception {
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(get("/operacoes")
							.header("X-API-Key", "cambia_" + "y".repeat(43))
							.header("X-Forwarded-For", "203.0.113.79"))
					.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(get("/operacoes")
						.header("X-API-Key", "cambia_" + "y".repeat(43))
						.header("X-Forwarded-For", "203.0.113.79"))
				.andExpect(status().isTooManyRequests());
	}

}
