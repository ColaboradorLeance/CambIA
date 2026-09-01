package com.cambia.operacao;

import java.time.LocalDate;

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
class RelatorioOperacoesControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String authHeader;
	private Long clienteAId;
	private Long clienteBId;
	private Long tlxId;
	private Long bzaId;

	@BeforeEach
	void preparar() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		authHeader = "Bearer " + token;

		clienteAId = criarCliente("Cliente A LTDA", "14.777.639/0001-92");
		clienteBId = criarCliente("Cliente B LTDA", "22.333.444/0001-55");
		tlxId = criarBanco("TLX", "N*70%");
		bzaId = criarBanco("BZA", "N*50%");
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

	private String criarOperacao(String data, Long clienteId, Long bancoId, String cv) throws Exception {
		String json = """
				{"data":"%s","clienteId":%d,"bancoId":%d,"cv":"%s","prCrVir":"Credito","moeda":"USD",
				"valorMe":1000,"spotAsset":5.10,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(data, clienteId, bancoId, cv);

		return mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andReturn().getResponse().getHeader("Location");
	}

	private void completar(String location) throws Exception {
		mockMvc.perform(patch(location + "/status")
				.header("Authorization", authHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CONFIRMADO\"}"));
	}

	@Test
	void periodoHojeRetornaSoOperacoesDeHoje() throws Exception {
		String hoje = LocalDate.now().toString();
		criarOperacao(hoje, clienteAId, tlxId, "V");
		criarOperacao("2020-01-01", clienteAId, tlxId, "V");

		mockMvc.perform(get("/relatorios/operacoes").param("periodo", "HOJE").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].data").value(hoje));
	}

	@Test
	void filtraPorClienteDentroDoPeriodo() throws Exception {
		String hoje = LocalDate.now().toString();
		criarOperacao(hoje, clienteAId, tlxId, "V");
		criarOperacao(hoje, clienteBId, tlxId, "V");

		mockMvc.perform(get("/relatorios/operacoes")
						.param("periodo", "HOJE")
						.param("clienteId", clienteAId.toString())
						.header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].clienteId").value(clienteAId));
	}

	@Test
	void filtraPorBancoEStatusCombinados() throws Exception {
		String hoje = LocalDate.now().toString();
		String op1 = criarOperacao(hoje, clienteAId, tlxId, "V");
		completar(op1);
		criarOperacao(hoje, clienteAId, tlxId, "V"); // fica em andamento
		criarOperacao(hoje, clienteAId, bzaId, "V"); // banco diferente, completa depois

		mockMvc.perform(get("/relatorios/operacoes")
						.param("periodo", "HOJE")
						.param("bancoId", tlxId.toString())
						.param("status", "CONFIRMADO")
						.header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].bancoId").value(tlxId))
				.andExpect(jsonPath("$[0].status").value("CONFIRMADO"));
	}

	@Test
	void anoAbrangeOperacoesDoAnoCorrenteEExcluiAnoPassado() throws Exception {
		String hoje = LocalDate.now().toString();
		criarOperacao(hoje, clienteAId, tlxId, "V");
		criarOperacao("2019-06-15", clienteAId, tlxId, "V");

		mockMvc.perform(get("/relatorios/operacoes").param("periodo", "ANO").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].data").value(hoje));
	}

	@Test
	void periodoAusenteRetorna400() throws Exception {
		mockMvc.perform(get("/relatorios/operacoes").header("Authorization", authHeader))
				.andExpect(status().isBadRequest());
	}

}
