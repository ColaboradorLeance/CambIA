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
class OperacaoHistoricoTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String authHeader;
	private Long clienteId;
	private Long bancoId;

	@BeforeEach
	void preparar() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN,
				"Admin");
		authHeader = "Bearer " + token;

		clienteId = criarCliente("Cliente Historico LTDA", "14.777.639/0001-92");
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

	@Test
	void registraEventoDeCriacaoComQuemCriou() throws Exception {
		String json = """
				{"data":"2026-08-26","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Credito","fundo":"M","spreadEmissao":"0.020","moeda":"USD",
				"valorMe":1000,"spotAsset":5.10,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId);

		String location = mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andReturn().getResponse().getHeader("Location");
		String idTrade = JsonPath.read(mockMvc.perform(get(location).header("Authorization", authHeader))
				.andReturn().getResponse().getContentAsString(), "$.idTrade");

		mockMvc.perform(get("/operacoes/historico").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].idTrade").value(idTrade))
				.andExpect(jsonPath("$[0].tipo").value("CRIADA"))
				.andExpect(jsonPath("$[0].usuarioNome").value("Admin"))
				.andExpect(jsonPath("$[0].dadosAnteriores.moeda").value("USD"))
				.andExpect(jsonPath("$[0].dadosAnteriores.valorMe").value(1000.00));
	}

	@Test
	void registraEventosDeEdicaoECompletadaComDadosAnterioresNaEdicao() throws Exception {
		String criarJson = """
				{"data":"2026-08-26","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Credito","fundo":"M","spreadEmissao":"0.020","moeda":"USD",
				"valorMe":1000,"spotAsset":5.10,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId);
		String location = mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(criarJson))
				.andReturn().getResponse().getHeader("Location");

		String edicaoJson = """
				{"data":"2026-08-27","clienteId":%d,"bancoId":%d,"cv":"C","prCrVir":"Pronto","fundo":"P","spreadEmissao":"NA","moeda":"EUR",
				"valorMe":500,"spotAsset":5.30,"nivelamento":5.30,"taxaFinal":5.10}
				""".formatted(clienteId, bancoId);
		mockMvc.perform(put(location)
				.header("Authorization", authHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.content(edicaoJson));

		mockMvc.perform(patch(location + "/status")
				.header("Authorization", authHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CONFIRMADO\"}"));

		mockMvc.perform(get("/operacoes/historico").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				// mais recente primeiro
				.andExpect(jsonPath("$[0].tipo").value("CONFIRMADA"))
				.andExpect(jsonPath("$[0].dadosAnteriores").doesNotExist())
				.andExpect(jsonPath("$[1].tipo").value("EDITADA"))
				.andExpect(jsonPath("$[1].dadosAnteriores.moeda").value("USD"))
				.andExpect(jsonPath("$[1].dadosAnteriores.valorMe").value(1000.00))
				.andExpect(jsonPath("$[1].usuarioNome").value("Admin"))
				.andExpect(jsonPath("$[2].tipo").value("CRIADA"));
	}

	@Test
	void consultorConsegueVerHistorico() throws Exception {
		String consultorToken = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository,
				Perfil.CONSULTOR);

		mockMvc.perform(get("/operacoes/historico").header("Authorization", "Bearer " + consultorToken))
				.andExpect(status().isOk());
	}

}
