package com.cambia.operacao;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Incremento 78: bug crítico encontrado no teste de carga de 2026-10-08 — o Código da
 * Ordem (idTrade) era gerado como "quantidade de ordens do ano + 1", então duas
 * gravações simultâneas pegavam o MESMO número e uma delas falhava na constraint de
 * unicidade (69% de falha com 10 gravações concorrentes; 3 de 6 já com 6).
 *
 * <p>Este teste NÃO é @Transactional de propósito: as requisições precisam rodar em
 * transações reais e concorrentes pra reproduzir a corrida. Por isso os dados criados
 * são apagados no @AfterEach, e as ordens usam o ano 2031 (contador próprio, fora do
 * período "hoje/mês" que os testes de relatório consultam).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OperacaoIdTradeConcorrenciaTests {

	private static final int GRAVACOES_SIMULTANEAS = 20;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private String authHeader;
	private Long clienteId;
	private Long bancoId;
	private Long calculoId;

	@BeforeEach
	void preparar() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		authHeader = "Bearer " + token;

		String clienteBody = mockMvc.perform(post("/clientes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Cliente Concorrencia\",\"documento\":\"66.666.666/0001-66\"}"))
				.andReturn().getResponse().getContentAsString();
		clienteId = ((Number) JsonPath.read(clienteBody, "$.id")).longValue();

		String calculoBody = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Modelo Concorrencia\",\"formula\":\"N*50%\"}"))
				.andReturn().getResponse().getContentAsString();
		calculoId = ((Number) JsonPath.read(calculoBody, "$.id")).longValue();

		String bancoBody = mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"993\",\"sigla\":\"CC\",\"nome\":\"CC\",\"taxaRebate\":0,\"calculoId\":%d}"
								.formatted(calculoId)))
				.andReturn().getResponse().getContentAsString();
		bancoId = ((Number) JsonPath.read(bancoBody, "$.id")).longValue();
	}

	@AfterEach
	void limpar() {
		transactionTemplate.executeWithoutResult(status -> {
			entityManager.createNativeQuery("""
					DELETE FROM operacoes_eventos WHERE operacao_id IN
					(SELECT id FROM operacoes WHERE cliente_id = :clienteId)""")
					.setParameter("clienteId", clienteId).executeUpdate();
			entityManager.createNativeQuery("DELETE FROM operacoes WHERE cliente_id = :clienteId")
					.setParameter("clienteId", clienteId).executeUpdate();
			entityManager.createNativeQuery("DELETE FROM clientes WHERE id = :id")
					.setParameter("id", clienteId).executeUpdate();
			entityManager.createNativeQuery("DELETE FROM bancos WHERE id = :id")
					.setParameter("id", bancoId).executeUpdate();
			entityManager.createNativeQuery("DELETE FROM modelos_calculo WHERE id = :id")
					.setParameter("id", calculoId).executeUpdate();
		});
	}

	@Test
	void gravacoesSimultaneasGeramCodigosDistintosSemFalhar() throws Exception {
		String json = """
				{"data":"2031-01-15","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Pronto","fundo":"P",
				"spreadEmissao":"NA","moeda":"USD","valorMe":1000,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId);

		ExecutorService executor = Executors.newFixedThreadPool(GRAVACOES_SIMULTANEAS);
		CountDownLatch largada = new CountDownLatch(1);
		List<Future<MockHttpServletResponse>> futuros = new ArrayList<>();
		try {
			for (int i = 0; i < GRAVACOES_SIMULTANEAS; i++) {
				futuros.add(executor.submit(() -> {
					largada.await(); // todas as threads disparam juntas
					return mockMvc.perform(post("/operacoes")
									.header("Authorization", authHeader)
									.contentType(MediaType.APPLICATION_JSON)
									.content(json))
							.andReturn().getResponse();
				}));
			}
			largada.countDown();

			Set<String> codigos = new HashSet<>();
			for (Future<MockHttpServletResponse> futuro : futuros) {
				MockHttpServletResponse resposta = futuro.get();
				assertEquals(201, resposta.getStatus(), "gravação falhou: " + resposta.getContentAsString());
				codigos.add(JsonPath.read(resposta.getContentAsString(), "$.idTrade"));
			}
			assertEquals(GRAVACOES_SIMULTANEAS, codigos.size(), "códigos repetidos: " + codigos);
		} finally {
			executor.shutdownNow();
		}
	}

}
