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
class CalculoControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String authHeader;

	private static final String CALCULO_JSON = """
			{"nome":"Setenta por cento simples","formula":"N*70%"}
			""";

	@BeforeEach
	void autenticarComoAdmin() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		authHeader = "Bearer " + token;
	}

	@Test
	void criaEBuscaCalculo() throws Exception {
		String location = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(CALCULO_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.nome").value("Setenta por cento simples"))
				.andExpect(jsonPath("$.formula").value("N*70%"))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(get(location).header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.formula").value("N*70%"));
	}

	@Test
	void aceitaFormulaComplexaComoADaTlx() throws Exception {
		String json = """
				{"nome":"TLX com desconto","formula":"N*70%-N*70%*4,65%"}
				""";

		mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.formula").value("N*70%-N*70%*4,65%"));
	}

	@Test
	void listaCalculosCadastrados() throws Exception {
		mockMvc.perform(post("/calculos")
				.header("Authorization", authHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.content(CALCULO_JSON));

		mockMvc.perform(get("/calculos").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$[?(@.nome=='Setenta por cento simples')]").exists());
	}

	@Test
	void atualizaCalculo() throws Exception {
		String location = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(CALCULO_JSON))
				.andReturn().getResponse().getHeader("Location");

		String atualizado = """
				{"nome":"75% simples","formula":"N*75%"}
				""";

		mockMvc.perform(put(location)
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(atualizado))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.formula").value("N*75%"));

		mockMvc.perform(get(location).header("Authorization", authHeader))
				.andExpect(jsonPath("$.formula").value("N*75%"));
	}

	@Test
	void removeCalculoNaoUtilizado() throws Exception {
		String location = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(CALCULO_JSON))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(delete(location).header("Authorization", authHeader))
				.andExpect(status().isNoContent());

		mockMvc.perform(get(location).header("Authorization", authHeader))
				.andExpect(status().isNotFound());
	}

	@Test
	void naoRemoveCalculoEmUsoPorUmBanco() throws Exception {
		String calculoBody = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(CALCULO_JSON))
				.andReturn().getResponse().getContentAsString();
		Long calculoId = ((Number) JsonPath.read(calculoBody, "$.id")).longValue();

		mockMvc.perform(post("/bancos")
				.header("Authorization", authHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigoBanco\":\"001\",\"sigla\":\"BZA\",\"nome\":\"BZA\",\"taxaRebate\":0,\"calculoId\":%d}"
						.formatted(calculoId)));

		mockMvc.perform(delete("/calculos/" + calculoId).header("Authorization", authHeader))
				.andExpect(status().isConflict());
	}

	@Test
	void retorna404ParaCalculoInexistente() throws Exception {
		mockMvc.perform(get("/calculos/999999").header("Authorization", authHeader))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejeitaCadastroSemNome() throws Exception {
		String semNome = """
				{"nome":"","formula":"N*70%"}
				""";

		mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(semNome))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejeitaCadastroSemFormula() throws Exception {
		String semFormula = """
				{"nome":"BZA","formula":""}
				""";

		mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(semFormula))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejeitaFormulaComSintaxeInvalida() throws Exception {
		String formulaInvalida = """
				{"nome":"BZA","formula":"N*(70%"}
				""";

		mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(formulaInvalida))
				.andExpect(status().isBadRequest());
	}

	@Test
	void testaFormulaValidaRetornaResultado() throws Exception {
		String json = """
				{"formula":"N*70%-N*70%*4,65%","totalBrutoCambioExemplo":9206.52,"taxaRebateExemplo":0}
				""";

		mockMvc.perform(post("/calculos/teste")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.resultado").value(6144.89));
	}

	@Test
	void testaFormulaInvalidaRetorna400() throws Exception {
		String json = """
				{"formula":"N*(70%","totalBrutoCambioExemplo":1000,"taxaRebateExemplo":0}
				""";

		mockMvc.perform(post("/calculos/teste")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isBadRequest());
	}

}
