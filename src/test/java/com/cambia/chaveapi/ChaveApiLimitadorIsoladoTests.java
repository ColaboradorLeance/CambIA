package com.cambia.chaveapi;

import com.cambia.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremento 80 (pendência #18): o limitador de tentativas com chave inválida tinha o MESMO
 * teto de chaves rastreadas do login. Chaves inválidas vindas de muitos IPs enchiam esse
 * teto e o /auth/magic-link passava a responder 429 pra qualquer e-mail novo — um ataque à
 * integração derrubava o login das pessoas. Com tetos minúsculos (3) nos dois, este teste
 * só passa se os dois limitadores forem independentes.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"cambia.rate-limit.chave-api.maximo-chaves-rastreadas=3",
		"cambia.rate-limit.auth.maximo-chaves-rastreadas=3"
})
class ChaveApiLimitadorIsoladoTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void chavesInvalidasDeMuitosIpsNaoBloqueiamOLogin() throws Exception {
		for (int i = 1; i <= 5; i++) {
			mockMvc.perform(get("/operacoes")
					.header("X-API-Key", "cambia_" + "z".repeat(43))
					.header("X-Forwarded-For", "198.51.100." + i));
		}

		mockMvc.perform(post("/auth/magic-link")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"pessoa-isolamento@cambia.com.br\"}"))
				.andExpect(status().isAccepted());
	}

}
