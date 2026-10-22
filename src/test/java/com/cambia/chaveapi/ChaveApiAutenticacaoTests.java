package com.cambia.chaveapi;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.NomesCookieAuth;
import com.cambia.auth.TestAuthSupport;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.Usuario;
import com.cambia.usuario.UsuarioRepository;
import com.cambia.usuario.UsuarioTestFactory;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremento 79 (A1–A9, A11 de docs/chaves-api-plano.md): uso da chave no header
 * X-API-Key. A chave age como o usuário vinculado (D2/D6), nunca alcança gestão de
 * usuários/chaves/sessão (D7) e dispensa CSRF (D8). A10 (limite de tentativas) fica em
 * ChaveApiRateLimitTests, que precisa de um limite baixo num contexto Spring próprio.
 */
@Import({ TestcontainersConfiguration.class, RelogioAjustavelConfig.class })
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChaveApiAutenticacaoTests {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
	private static final String HEADER = "X-API-Key";

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

	private String adminToken;
	private String adminAuth;
	private Usuario tecnico;
	private Long clienteId;
	private Long bancoId;

	@BeforeEach
	void preparar() throws Exception {
		relogio.zerar();
		adminToken = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		adminAuth = "Bearer " + adminToken;
		tecnico = usuarioRepository.save(UsuarioTestFactory.novo("Integração Beyond Teste",
				"beyond-" + UUID.randomUUID() + "@cambia.com.br", Perfil.ANALISTA));

		String cliente = mockMvc.perform(post("/clientes")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Cliente Chave API\",\"documento\":\"77.777.777/0001-77\"}"))
				.andReturn().getResponse().getContentAsString();
		clienteId = ((Number) JsonPath.read(cliente, "$.id")).longValue();

		String calculo = mockMvc.perform(post("/calculos")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Modelo Chave API\",\"formula\":\"N*50%\"}"))
				.andReturn().getResponse().getContentAsString();
		Long calculoId = ((Number) JsonPath.read(calculo, "$.id")).longValue();

		String banco = mockMvc.perform(post("/bancos")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"992\",\"sigla\":\"CA\",\"nome\":\"CA\",\"taxaRebate\":0,\"calculoId\":%d}"
								.formatted(calculoId)))
				.andReturn().getResponse().getContentAsString();
		bancoId = ((Number) JsonPath.read(banco, "$.id")).longValue();
	}

	private String criarChave(Long usuarioId, LocalDate expiraEm) throws Exception {
		String expira = expiraEm == null ? "null" : "\"" + expiraEm + "\"";
		String corpo = mockMvc.perform(post("/chaves-api")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Chave\",\"usuarioId\":%d,\"expiraEm\":%s}".formatted(usuarioId, expira)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(corpo, "$.chave");
	}

	private String ordemJson() {
		return """
				{"data":"2026-09-01","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Pronto","fundo":"P",
				"spreadEmissao":"NA","moeda":"USD","valorMe":1000,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId);
	}

	private org.springframework.test.web.servlet.ResultActions criarOrdemCom(String chave) throws Exception {
		return mockMvc.perform(post("/operacoes")
				.header(HEADER, chave)
				.contentType(MediaType.APPLICATION_JSON)
				.content(ordemJson()));
	}

	@Test
	void chaveValidaCriaOrdemComoUsuarioVinculado() throws Exception {
		String chave = criarChave(tecnico.getId(), null);

		String corpo = criarOrdemCom(chave)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.criadoPorNome").value("Integração Beyond Teste"))
				.andReturn().getResponse().getContentAsString();

		Long operacaoId = ((Number) JsonPath.read(corpo, "$.id")).longValue();
		Number usuarioDoEvento = (Number) entityManager
				.createNativeQuery("SELECT usuario_id FROM operacoes_eventos WHERE operacao_id = :id")
				.setParameter("id", operacaoId)
				.getSingleResult();
		assertEquals(tecnico.getId(), usuarioDoEvento.longValue());
	}

	@Test
	void chaveRespeitaPerfilDoUsuario() throws Exception {
		Usuario consultor = usuarioRepository.save(UsuarioTestFactory.novo("Consultor Integração",
				"consultor-" + UUID.randomUUID() + "@cambia.com.br", Perfil.CONSULTOR));
		String chave = criarChave(consultor.getId(), null);

		mockMvc.perform(get("/operacoes").header(HEADER, chave))
				.andExpect(status().isOk());
		criarOrdemCom(chave)
				.andExpect(status().isForbidden());
	}

	@Test
	void chaveInexistenteRetorna401() throws Exception {
		mockMvc.perform(get("/operacoes").header(HEADER, "cambia_" + "x".repeat(43)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail", containsString("Chave de API")));
	}

	@Test
	void chaveRevogadaRetorna401() throws Exception {
		String corpo = mockMvc.perform(post("/chaves-api")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Chave\",\"usuarioId\":%d}".formatted(tecnico.getId())))
				.andReturn().getResponse().getContentAsString();
		String chave = JsonPath.read(corpo, "$.chave");
		Number id = JsonPath.read(corpo, "$.id");

		criarOrdemCom(chave).andExpect(status().isCreated());
		mockMvc.perform(delete("/chaves-api/" + id).header("Authorization", adminAuth))
				.andExpect(status().isNoContent());
		criarOrdemCom(chave).andExpect(status().isUnauthorized());
	}

	@Test
	void chaveExpiradaRetorna401() throws Exception {
		// Vale até o fim do dia informado (fuso de São Paulo); dois dias depois, não vale mais.
		String chave = criarChave(tecnico.getId(), LocalDate.now(FUSO));
		criarOrdemCom(chave).andExpect(status().isCreated());

		relogio.avancar(Duration.ofDays(2));

		criarOrdemCom(chave).andExpect(status().isUnauthorized());
	}

	@Test
	void chaveNaoGerenciaUsuariosChavesNemSessao() throws Exception {
		String chave = criarChave(tecnico.getId(), null);

		mockMvc.perform(get("/usuarios").header(HEADER, chave))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/chaves-api").header(HEADER, chave))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/chaves-api")
						.header(HEADER, chave)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"X\",\"usuarioId\":%d}".formatted(tecnico.getId())))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/auth/sessao").header(HEADER, chave))
				.andExpect(status().isForbidden());
	}

	@Test
	void caminhoCodificadoNaoBurlaBloqueio() throws Exception {
		// Achado da revisão de segurança: "/%75suarios" é "/usuarios" pro roteamento do
		// Spring, mas não começava com "/usuarios" na comparação crua do filtro.
		String chave = criarChave(tecnico.getId(), null);

		// URI.create: com uma String, o MockMvc trata o caminho como template e codifica o
		// "%" de novo ("%2575"), que o firewall do Spring barra com 400 antes do filtro —
		// o teste passaria sem exercitar o cenário real.
		// Rota pública (/auth) de propósito: em /usuarios a chave de Analista já levaria 403
		// pela regra de perfil, e o teste passaria mesmo sem o bloqueio da D7 funcionar. A
		// mensagem distingue os dois 403 (perfil: "Seu perfil não tem permissão").
		mockMvc.perform(delete(java.net.URI.create("/%61uth/sessao")).header(HEADER, chave))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString("Chave de API não pode")));
		mockMvc.perform(get(java.net.URI.create("/%75suarios")).header(HEADER, chave))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString("Chave de API não pode")));
	}

	@Test
	void chaveDeUsuarioPromovidoAAdminDeixaDeValer() throws Exception {
		// Achado da revisão de segurança: D3 (nunca Admin) era checada só na criação; se o
		// usuário vinculado virasse Admin depois, a chave passava a agir como Admin.
		String chave = criarChave(tecnico.getId(), null);
		criarOrdemCom(chave).andExpect(status().isCreated());

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/usuarios/" + tecnico.getId())
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"%s\",\"email\":\"%s\",\"perfil\":\"ADMIN\"}"
								.formatted(tecnico.getNome(), tecnico.getEmail())))
				.andExpect(status().isOk());

		criarOrdemCom(chave).andExpect(status().isUnauthorized());
	}

	@Test
	void chaveNaoPrecisaDeCsrf() throws Exception {
		// Pior caso: chave E cookie de sessão (de um Admin) na mesma requisição, sem o
		// header X-XSRF-TOKEN. A chave vence (a ordem fica em nome do usuário técnico) e
		// o CSRF não se aplica, porque não foi o cookie que autenticou.
		String chave = criarChave(tecnico.getId(), null);

		mockMvc.perform(post("/operacoes")
						.header(HEADER, chave)
						.cookie(new Cookie(NomesCookieAuth.SESSAO, adminToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(ordemJson()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.criadoPorNome").value("Integração Beyond Teste"));
	}

	private String ultimoUso() throws Exception {
		String lista = mockMvc.perform(get("/chaves-api").header("Authorization", adminAuth))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(lista, "$[0].ultimoUsoEm");
	}

	@Test
	void registraUltimoUso() throws Exception {
		String chave = criarChave(tecnico.getId(), null);
		assertNull(ultimoUso());

		mockMvc.perform(get("/operacoes").header(HEADER, chave)).andExpect(status().isOk());
		String primeiro = ultimoUso();
		assertNotNull(primeiro);

		// Uso logo em seguida não regrava (janela de 5 minutos evita um UPDATE por requisição).
		relogio.avancar(Duration.ofMinutes(1));
		mockMvc.perform(get("/operacoes").header(HEADER, chave)).andExpect(status().isOk());
		assertEquals(primeiro, ultimoUso());

		relogio.avancar(Duration.ofMinutes(6));
		mockMvc.perform(get("/operacoes").header(HEADER, chave)).andExpect(status().isOk());
		assertNotEquals(primeiro, ultimoUso());
	}

	@Test
	void headerVazioOuMalformadoNaoAutentica() throws Exception {
		mockMvc.perform(get("/operacoes").header(HEADER, ""))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/operacoes").header(HEADER, "abc"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void sessaoContinuaFuncionandoSemHeader() throws Exception {
		mockMvc.perform(get("/operacoes").header("Authorization", adminAuth))
				.andExpect(status().isOk());
	}

}
