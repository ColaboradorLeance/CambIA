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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremento 84: banco com um ou mais modelos de cálculo (antes, exatamente um). Regras em
 * docs/decisoes.md ("Banco com mais de um modelo de cálculo").
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BancoMultiplosCalculosTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String auth;
	private Long calculoA;
	private Long calculoB;

	@BeforeEach
	void preparar() throws Exception {
		auth = "Bearer " + TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		calculoA = criar("/calculos", "{\"nome\":\"A Metade\",\"formula\":\"N*50%\"}");
		calculoB = criar("/calculos", "{\"nome\":\"B Setenta\",\"formula\":\"N*70%\"}");
	}

	private Long criar(String caminho, String json) throws Exception {
		String corpo = mockMvc.perform(post(caminho)
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(corpo, "$.id")).longValue();
	}

	private String bancoJson(String calculos) {
		return "{\"codigoBanco\":\"981\",\"sigla\":\"MC\",\"nome\":\"Banco Multi\",\"taxaRebate\":0,%s}".formatted(calculos);
	}

	@Test
	void criaBancoComVariosCalculos() throws Exception {
		mockMvc.perform(post("/bancos")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(bancoJson("\"calculoIds\":[%d,%d]".formatted(calculoB, calculoA))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.calculos", hasSize(2)))
				// em ordem de nome, pra tela e select ficarem estáveis
				.andExpect(jsonPath("$.calculos[0].id").value(calculoA))
				.andExpect(jsonPath("$.calculos[0].nome").value("A Metade"))
				.andExpect(jsonPath("$.calculos[0].formula").value("N*50%"))
				.andExpect(jsonPath("$.calculos[1].id").value(calculoB));
	}

	@Test
	void aceitaCalculoIdUnicoPorCompatibilidade() throws Exception {
		mockMvc.perform(post("/bancos")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(bancoJson("\"calculoId\":%d".formatted(calculoA))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.calculos", hasSize(1)))
				.andExpect(jsonPath("$.calculos[0].id").value(calculoA));
	}

	@Test
	void calculoRepetidoNaListaContaUmaVez() throws Exception {
		mockMvc.perform(post("/bancos")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(bancoJson("\"calculoIds\":[%d,%d]".formatted(calculoA, calculoA))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.calculos", hasSize(1)));
	}

	@Test
	void exigeAoMenosUmCalculo() throws Exception {
		mockMvc.perform(post("/bancos")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(bancoJson("\"calculoIds\":[]")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("cálculo")));
		mockMvc.perform(post("/bancos")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(bancoJson("\"sigla2\":\"x\"")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejeitaCalculoInexistente() throws Exception {
		mockMvc.perform(post("/bancos")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(bancoJson("\"calculoIds\":[%d,999999]".formatted(calculoA))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void edicaoTrocaOsCalculos() throws Exception {
		Long banco = criar("/bancos", bancoJson("\"calculoIds\":[%d,%d]".formatted(calculoA, calculoB)));

		mockMvc.perform(put("/bancos/" + banco)
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(bancoJson("\"calculoIds\":[%d]".formatted(calculoB))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.calculos", hasSize(1)))
				.andExpect(jsonPath("$.calculos[0].id").value(calculoB));
	}

	@Test
	void naoRemoveModeloEmUsoPorAlgumBanco() throws Exception {
		criar("/bancos", bancoJson("\"calculoIds\":[%d,%d]".formatted(calculoA, calculoB)));

		mockMvc.perform(delete("/calculos/" + calculoB).header("Authorization", auth))
				.andExpect(status().isConflict());
	}

	@Test
	void naoRemoveModeloUsadoPorOrdemMesmoSeNenhumBancoUsaMais() throws Exception {
		// Ordem com o cálculo B; depois o banco deixa de ter B. A ordem continua com B
		// (decisão do PO), então o modelo B não pode sumir do sistema.
		Long banco = criar("/bancos", bancoJson("\"calculoIds\":[%d,%d]".formatted(calculoA, calculoB)));
		Long cliente = criar("/clientes", "{\"nome\":\"Cliente Multi\",\"documento\":\"12.345.678/0001-77\"}");
		Long ordem = criar("/operacoes", """
				{"data":"2026-09-01","clienteId":%d,"bancoId":%d,"calculoId":%d,"cv":"V","prCrVir":"Pronto","fundo":"P",
				"spreadEmissao":"NA","moeda":"USD","valorMe":1000,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(cliente, banco, calculoB));
		mockMvc.perform(patch("/operacoes/" + ordem + "/status")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"CONFIRMADO\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(put("/bancos/" + banco)
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(bancoJson("\"calculoIds\":[%d]".formatted(calculoA))))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/calculos/" + calculoB).header("Authorization", auth))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail", containsString("ordens")));
	}

}
