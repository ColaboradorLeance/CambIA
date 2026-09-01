package com.cambia;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class HealthCheckTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthEndpointReportsApplicationUp() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	// Achado de revisão de segurança: /actuator/health é público (precisa ser, pro
	// healthcheck do Docker), mas não deve expor detalhe interno (host de e-mail, disco,
	// etc.) — nem pra quem não está logado, nem pra quem está (ver application.properties).
	@Test
	void healthEndpointNaoExpoeDetalhesInternos() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components").doesNotExist());
	}

}
