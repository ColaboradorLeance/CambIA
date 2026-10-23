package com.cambia.chaveapi;

import java.net.URI;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import com.cambia.auth.TestAuthSupport.Sessao;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static com.cambia.chaveapi.ChaveApiTestSupport.CRIAR_ORDENS;
import static com.cambia.chaveapi.ChaveApiTestSupport.HEADER;
import static com.cambia.chaveapi.ChaveApiTestSupport.LEITURA;
import static com.cambia.chaveapi.ChaveApiTestSupport.clienteEBanco;
import static com.cambia.chaveapi.ChaveApiTestSupport.criarChave;
import static com.cambia.chaveapi.ChaveApiTestSupport.id;
import static com.cambia.chaveapi.ChaveApiTestSupport.ordemJson;
import static com.cambia.chaveapi.ChaveApiTestSupport.segredo;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremento 81 (E1–E9, R1 e R2 de docs/chaves-api-usuario-plano.md): a chave age como o
 * dono (K2), só dentro do escopo (K4: LEITURA = GET em operações, clientes e bancos;
 * CRIAR_ORDENS = POST /operacoes), limitada pelo perfil do dono (K6), e o evento da ordem
 * registra qual chave foi usada (K9). Nenhum escopo edita, confirma ou cancela ordem (R1).
 */
@Import({ TestcontainersConfiguration.class, RelogioAjustavelConfig.class })
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChaveApiEscopoTests {

	private static final String MENSAGEM_ESCOPO = "escopo";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private RelogioAjustavelConfig.RelogioAjustavel relogio;

	private Sessao adm;
	private Sessao ana;
	private long clienteId;
	private long bancoId;

	@BeforeEach
	void preparar() throws Exception {
		relogio.zerar();
		adm = TestAuthSupport.obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN, "Adm");
		ana = TestAuthSupport.obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ANALISTA, "Ana");
		long[] ids = clienteEBanco(mockMvc, adm.bearer(), "Escopo");
		clienteId = ids[0];
		bancoId = ids[1];
	}

	private ResultActions criarOrdemCom(String chave) throws Exception {
		return mockMvc.perform(post("/operacoes")
				.header(HEADER, chave)
				.contentType(MediaType.APPLICATION_JSON)
				.content(ordemJson(clienteId, bancoId)));
	}

	private long criarOrdemPorSessao() throws Exception {
		return id(mockMvc.perform(post("/operacoes")
						.header("Authorization", ana.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content(ordemJson(clienteId, bancoId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	private void mudarPerfilDaAna(Perfil perfil) throws Exception {
		mockMvc.perform(put("/usuarios/" + ana.usuario().getId())
						.header("Authorization", adm.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Ana\",\"email\":\"%s\",\"perfil\":\"%s\"}"
								.formatted(ana.usuario().getEmail(), perfil.name())))
				.andExpect(status().isOk());
	}

	@Test
	void criarOrdensPermitePostOperacoes() throws Exception {
		String chave = segredo(criarChave(mockMvc, ana.bearer(), "ERP", null, CRIAR_ORDENS));

		criarOrdemCom(chave)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.criadoPorNome").value("Ana"));
	}

	@Test
	void leituraPermiteGetsDaIntegracao() throws Exception {
		String chave = segredo(criarChave(mockMvc, ana.bearer(), "ERP leitura", null, LEITURA));

		mockMvc.perform(get("/operacoes/pagina").param("status", "ANDAMENTO").param("pagina", "0").param("tamanho", "10")
						.header(HEADER, chave))
				.andExpect(status().isOk());
		mockMvc.perform(get("/operacoes/historico").header(HEADER, chave)).andExpect(status().isOk());
		mockMvc.perform(get("/clientes").header(HEADER, chave)).andExpect(status().isOk());
		mockMvc.perform(get("/bancos").header(HEADER, chave)).andExpect(status().isOk());
	}

	@Test
	void leituraNaoCriaOrdem() throws Exception {
		String chave = segredo(criarChave(mockMvc, ana.bearer(), "ERP leitura", null, LEITURA));

		criarOrdemCom(chave)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString(MENSAGEM_ESCOPO)));
	}

	@Test
	void nenhumEscopoEditaConfirmaOuCancela() throws Exception {
		String chave = segredo(criarChave(mockMvc, ana.bearer(), "ERP", null, LEITURA, CRIAR_ORDENS));
		long ordem = criarOrdemPorSessao();

		mockMvc.perform(put("/operacoes/" + ordem)
						.header(HEADER, chave)
						.contentType(MediaType.APPLICATION_JSON)
						.content(ordemJson(clienteId, bancoId)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString(MENSAGEM_ESCOPO)));
		for (String novoStatus : new String[] { "CONFIRMADO", "CANCELADO" }) {
			mockMvc.perform(patch("/operacoes/" + ordem + "/status")
							.header(HEADER, chave)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"status\":\"%s\"}".formatted(novoStatus)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.detail", containsString(MENSAGEM_ESCOPO)));
		}
	}

	@Test
	void escopoNaoAbreRotasForaDaLista() throws Exception {
		// Ana (Analista) alcança relatórios, fechamento e cadastros pela tela; a chave não.
		String chave = segredo(criarChave(mockMvc, ana.bearer(), "ERP", null, LEITURA, CRIAR_ORDENS));

		mockMvc.perform(get("/relatorios/posicao-aberto").header(HEADER, chave))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString(MENSAGEM_ESCOPO)));
		mockMvc.perform(get("/fechamentos/historico").header(HEADER, chave))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString(MENSAGEM_ESCOPO)));
		mockMvc.perform(get("/calculos").header(HEADER, chave))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString(MENSAGEM_ESCOPO)));
		mockMvc.perform(post("/clientes")
						.header(HEADER, chave)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Pela chave\",\"documento\":\"11.111.111/0001-11\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString(MENSAGEM_ESCOPO)));
	}

	@Test
	void perfilRebaixadoPerdeOEscopo() throws Exception {
		String chave = segredo(criarChave(mockMvc, ana.bearer(), "ERP", null, LEITURA, CRIAR_ORDENS));
		criarOrdemCom(chave).andExpect(status().isCreated());

		mudarPerfilDaAna(Perfil.CONSULTOR);

		// K6: o escopo continua na chave, mas o perfil do dono não permite mais criar — o 403
		// vem da regra de perfil (SecurityConfig), não do escopo.
		criarOrdemCom(chave)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString("Seu perfil não tem permissão")));
		mockMvc.perform(get("/operacoes").header(HEADER, chave)).andExpect(status().isOk());
	}

	@Test
	void chaveNaoGerenciaChavesNemPerfil() throws Exception {
		String chave = segredo(criarChave(mockMvc, ana.bearer(), "ERP", null, LEITURA, CRIAR_ORDENS));

		mockMvc.perform(get("/perfil/chaves-api").header(HEADER, chave))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString("Chave de API não pode")));
		mockMvc.perform(post("/perfil/chaves-api")
						.header(HEADER, chave)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Filha\",\"escopos\":[\"LEITURA\"]}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString("Chave de API não pode")));
		mockMvc.perform(get("/chaves-api").header(HEADER, chave))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString("Chave de API não pode")));
		mockMvc.perform(get(URI.create("/%70erfil/chaves-api")).header(HEADER, chave))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString("Chave de API não pode")));
	}

	@Test
	void escopoPeloCaminhoDecodificado() throws Exception {
		// Mesma classe de achado do Inc. 79 (D7 pela URI crua): "/%6Fperacoes/1" é
		// "/operacoes/1" pro roteamento do Spring; o escopo tem que ver o caminho decodificado.
		String chave = segredo(criarChave(mockMvc, ana.bearer(), "ERP", null, LEITURA, CRIAR_ORDENS));
		long ordem = criarOrdemPorSessao();

		mockMvc.perform(put(URI.create("/%6Fperacoes/" + ordem))
						.header(HEADER, chave)
						.contentType(MediaType.APPLICATION_JSON)
						.content(ordemJson(clienteId, bancoId)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString(MENSAGEM_ESCOPO)));
		mockMvc.perform(get(URI.create("/%72elatorios/posicao-aberto")).header(HEADER, chave))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString(MENSAGEM_ESCOPO)));
	}

	@Test
	void donoPromovidoAAdminContinuaLimitadoAoEscopo() throws Exception {
		// K3 substitui o teste do Inc. 79 "chave de usuário promovido a Admin deixa de valer":
		// Admin pode ter chave, e o que limita é o escopo somado à D7.
		String chave = segredo(criarChave(mockMvc, ana.bearer(), "ERP", null, LEITURA, CRIAR_ORDENS));

		mudarPerfilDaAna(Perfil.ADMIN);

		criarOrdemCom(chave).andExpect(status().isCreated());
		mockMvc.perform(get("/usuarios").header(HEADER, chave))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString("Chave de API não pode")));
		mockMvc.perform(get("/chaves-api").header(HEADER, chave))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString("Chave de API não pode")));
		mockMvc.perform(get("/relatorios/posicao-aberto").header(HEADER, chave))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString(MENSAGEM_ESCOPO)));
	}

	@Test
	void eventoGuardaAChaveUsada() throws Exception {
		String corpo = criarChave(mockMvc, ana.bearer(), "ERP", null, CRIAR_ORDENS);
		long ordemPorChave = id(criarOrdemCom(segredo(corpo)).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
		long ordemPorSessao = criarOrdemPorSessao();

		assertEquals(id(corpo), chaveDoEvento(ordemPorChave).longValue());
		assertNull(chaveDoEvento(ordemPorSessao));
	}

	private Number chaveDoEvento(long operacaoId) {
		return (Number) entityManager
				.createNativeQuery("SELECT chave_api_id FROM operacoes_eventos WHERE operacao_id = :id AND tipo = 'CRIADA'")
				.setParameter("id", operacaoId)
				.getSingleResult();
	}

	@Test
	void historicoMostraAChave() throws Exception {
		String chave = segredo(criarChave(mockMvc, ana.bearer(), "Integração ERP, produção", null, CRIAR_ORDENS));
		long ordem = id(criarOrdemCom(chave).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());

		String historico = mockMvc.perform(get("/operacoes/historico").header("Authorization", ana.bearer()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		java.util.List<String> nomes = JsonPath.read(historico, "$[?(@.operacaoId == %d)].chaveApiNome".formatted(ordem));
		assertEquals(java.util.List.of("Integração ERP, produção"), nomes);
		java.util.List<String> usuarios = JsonPath.read(historico, "$[?(@.operacaoId == %d)].usuarioNome".formatted(ordem));
		assertEquals(java.util.List.of("Ana"), usuarios);
	}

}
