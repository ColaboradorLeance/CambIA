package com.cambia.operacao;

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
class OperacaoEdicaoTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String authHeader;
	private Long clienteId;
	private Long outroClienteId;
	private Long bancoId;

	@BeforeEach
	void preparar() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		authHeader = "Bearer " + token;

		clienteId = criarCliente("Cliente Original LTDA", "14.777.639/0001-92");
		outroClienteId = criarCliente("Cliente Novo LTDA", "22.333.444/0001-55");
		bancoId = criarBanco("TLX", "N*70%");
	}

	private Long criarCliente(String nome, String documento) throws Exception {
		String body = mockMvc.perform(post("/clientes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"%s\",\"documento\":\"%s\"}".formatted(nome, documento)))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private Long criarBanco(String nome, String formula) throws Exception {
		String calculoBody = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"%s\",\"formula\":\"%s\"}".formatted(nome, formula)))
				.andReturn().getResponse().getContentAsString();
		Long calculoId = ((Number) JsonPath.read(calculoBody, "$.id")).longValue();

		String body = mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"%s\",\"sigla\":\"%s\",\"nome\":\"%s\",\"taxaRebate\":0,\"calculoId\":%d}"
								.formatted("999", nome, nome, calculoId)))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private String criarOperacao(Long clienteId, Long bancoId, String valorMe) throws Exception {
		String json = """
				{"data":"2026-08-26","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Credito","moeda":"USD",
				"valorMe":%s,"spotAsset":5.10,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId, valorMe);

		return mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andReturn().getResponse().getHeader("Location");
	}

	private String edicaoJson(Long clienteId, Long bancoId, String valorMe) {
		return """
				{"data":"2026-08-27","clienteId":%d,"bancoId":%d,"cv":"C","prCrVir":"Pronto","moeda":"EUR",
				"valorMe":%s,"spotAsset":5.30,"nivelamento":5.30,"taxaFinal":5.10}
				""".formatted(clienteId, bancoId, valorMe);
	}

	@Test
	void editaOperacaoEmAndamentoAtualizaCamposManuaisExcetoData() throws Exception {
		String location = criarOperacao(clienteId, bancoId, "1000");

		mockMvc.perform(put(location)
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(edicaoJson(outroClienteId, bancoId, "500")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value("2026-08-26"))
				.andExpect(jsonPath("$.clienteId").value(outroClienteId))
				.andExpect(jsonPath("$.cv").value("C"))
				.andExpect(jsonPath("$.prCrVir").value("Pronto"))
				.andExpect(jsonPath("$.moeda").value("EUR"))
				.andExpect(jsonPath("$.valorMe").value(500.00));

		mockMvc.perform(get(location).header("Authorization", authHeader))
				.andExpect(jsonPath("$.moeda").value("EUR"));
	}

	@Test
	void editarOperacaoIgnoraTentativaDeMudarAData() throws Exception {
		String location = criarOperacao(clienteId, bancoId, "1000");

		// edicaoJson() envia "2026-08-27", diferente da data original ("2026-08-26") — deve ser ignorado.
		mockMvc.perform(put(location)
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(edicaoJson(clienteId, bancoId, "500")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value("2026-08-26"));

		mockMvc.perform(get(location).header("Authorization", authHeader))
				.andExpect(jsonPath("$.data").value("2026-08-26"));
	}

	@Test
	void naoPermiteEditarOperacaoJaCompleta() throws Exception {
		String location = criarOperacao(clienteId, bancoId, "1000");
		mockMvc.perform(patch(location + "/status")
				.header("Authorization", authHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CONFIRMADO\"}"));

		mockMvc.perform(put(location)
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(edicaoJson(clienteId, bancoId, "500")))
				.andExpect(status().isConflict());
	}

	@Test
	void consultorNaoPodeEditarNemCriarNemCompletarOperacao() throws Exception {
		String consultorToken = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository,
				Perfil.CONSULTOR);
		String consultorAuth = "Bearer " + consultorToken;

		String location = criarOperacao(clienteId, bancoId, "1000");

		mockMvc.perform(post("/operacoes")
						.header("Authorization", consultorAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(edicaoJson(clienteId, bancoId, "100")))
				.andExpect(status().isForbidden());

		mockMvc.perform(put(location)
						.header("Authorization", consultorAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(edicaoJson(clienteId, bancoId, "500")))
				.andExpect(status().isForbidden());

		mockMvc.perform(patch(location + "/status")
						.header("Authorization", consultorAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"CONFIRMADO\"}"))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/operacoes").header("Authorization", consultorAuth))
				.andExpect(status().isOk());
	}

	@Test
	void analistaPodeEditarOperacao() throws Exception {
		String analistaToken = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository,
				Perfil.ANALISTA);
		String analistaAuth = "Bearer " + analistaToken;

		String location = criarOperacao(clienteId, bancoId, "1000");

		mockMvc.perform(put(location)
						.header("Authorization", analistaAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(edicaoJson(clienteId, bancoId, "500")))
				.andExpect(status().isOk());
	}

}
