package com.cambia.banco;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BancoControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String authHeader;
	private Long calculoId;

	@BeforeEach
	void autenticarComoAdmin() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		authHeader = "Bearer " + token;

		String calculoBody = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"70% simples\",\"formula\":\"N*70%\"}"))
				.andReturn().getResponse().getContentAsString();
		calculoId = ((Number) JsonPath.read(calculoBody, "$.id")).longValue();
	}

	private String bancoJson(String codigoBanco, String sigla, String nome, String taxaRebate, Long calculoId) {
		return """
				{"codigoBanco":"%s","sigla":"%s","nome":"%s","taxaRebate":%s,"calculoId":%d}
				""".formatted(codigoBanco, sigla, nome, taxaRebate, calculoId);
	}

	@Test
	void criaEBuscaBanco() throws Exception {
		String location = mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(bancoJson("001", "BZA", "Banco BZA", "1.5", calculoId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.codigoBanco").value("001"))
				.andExpect(jsonPath("$.sigla").value("BZA"))
				.andExpect(jsonPath("$.nome").value("Banco BZA"))
				.andExpect(jsonPath("$.taxaRebate").value(1.5))
				.andExpect(jsonPath("$.calculoId").value(calculoId))
				.andExpect(jsonPath("$.calculoFormula").value("N*70%"))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(get(location).header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value("Banco BZA"));
	}

	@Test
	void listaBancosCadastrados() throws Exception {
		mockMvc.perform(post("/bancos")
				.header("Authorization", authHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.content(bancoJson("001", "BZA", "Banco BZA", "1.5", calculoId)));

		mockMvc.perform(get("/bancos").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$[?(@.nome=='Banco BZA')]").exists());
	}

	@Test
	void atualizaBanco() throws Exception {
		String location = mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(bancoJson("001", "BZA", "Banco BZA", "1.5", calculoId)))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(put(location)
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(bancoJson("002", "BZA2", "Banco BZA Renomeado", "2.0", calculoId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.codigoBanco").value("002"))
				.andExpect(jsonPath("$.sigla").value("BZA2"))
				.andExpect(jsonPath("$.nome").value("Banco BZA Renomeado"))
				.andExpect(jsonPath("$.taxaRebate").value(2.0));

		mockMvc.perform(get(location).header("Authorization", authHeader))
				.andExpect(jsonPath("$.nome").value("Banco BZA Renomeado"));
	}

	@Test
	void removeBanco() throws Exception {
		String location = mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(bancoJson("001", "BZA", "Banco BZA", "1.5", calculoId)))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(delete(location).header("Authorization", authHeader))
				.andExpect(status().isNoContent());

		mockMvc.perform(get(location).header("Authorization", authHeader))
				.andExpect(status().isNotFound());
	}

	@Test
	void retorna404ParaBancoInexistente() throws Exception {
		mockMvc.perform(get("/bancos/999999").header("Authorization", authHeader))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejeitaCadastroSemNome() throws Exception {
		mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(bancoJson("001", "BZA", "", "1.5", calculoId)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejeitaCadastroSemCalculo() throws Exception {
		String semCalculo = """
				{"codigoBanco":"001","sigla":"BZA","nome":"Banco BZA","taxaRebate":1.5}
				""";

		mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(semCalculo))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejeitaCalculoInexistente() throws Exception {
		mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(bancoJson("001", "BZA", "Banco BZA", "1.5", 999999L)))
				.andExpect(status().isBadRequest());
	}

}
