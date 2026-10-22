package com.cambia.chaveapi;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.Usuario;
import com.cambia.usuario.UsuarioRepository;
import com.cambia.usuario.UsuarioTestFactory;
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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremento 79 (C1–C10 de docs/chaves-api-plano.md): gestão de chaves de API em
 * /chaves-api — só Admin (D1), nunca vinculada a Admin (D3), expiração opcional e não
 * retroativa (D4), revogação definitiva (D5), segredo exibido uma única vez e nunca
 * gravado (D10).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChaveApiControllerTests {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	@Autowired
	private ChaveApiRepository chaveApiRepository;

	private String adminAuth;
	private Usuario tecnico;

	@BeforeEach
	void preparar() throws Exception {
		adminAuth = "Bearer " + TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository,
				Perfil.ADMIN, "Admin Chaves");
		tecnico = usuarioRepository.save(UsuarioTestFactory.novo("Integração Teste",
				"integracao-" + UUID.randomUUID() + "@cambia.com.br", Perfil.ANALISTA));
	}

	private String chaveJson(String nome, Long usuarioId, LocalDate expiraEm) {
		String expira = expiraEm == null ? "null" : "\"" + expiraEm + "\"";
		return "{\"nome\":\"%s\",\"usuarioId\":%d,\"expiraEm\":%s}".formatted(nome, usuarioId, expira);
	}

	private String criar(String nome, Long usuarioId, LocalDate expiraEm) throws Exception {
		return mockMvc.perform(post("/chaves-api")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(chaveJson(nome, usuarioId, expiraEm)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	void adminCriaChaveERecebeSegredoUmaVez() throws Exception {
		String corpo = criar("Integração Beyond", tecnico.getId(), null);
		String segredo = JsonPath.read(corpo, "$.chave");

		org.junit.jupiter.api.Assertions.assertTrue(segredo.startsWith("cambia_"));
		assertEquals(segredo.substring(0, 13), JsonPath.read(corpo, "$.prefixo"));
		assertEquals("Integração Teste", JsonPath.read(corpo, "$.usuarioNome"));

		mockMvc.perform(get("/chaves-api").header("Authorization", adminAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].chave").doesNotExist())
				.andExpect(jsonPath("$[0].hash").doesNotExist())
				.andExpect(content().string(not(containsString(segredo))));
	}

	@Test
	void analistaEConsultorNaoGerenciamChaves() throws Exception {
		for (Perfil perfil : new Perfil[] { Perfil.ANALISTA, Perfil.CONSULTOR }) {
			String auth = "Bearer " + TestAuthSupport.obterToken(mockMvc, usuarioRepository,
					magicLinkTokenRepository, perfil);

			mockMvc.perform(post("/chaves-api")
							.header("Authorization", auth)
							.contentType(MediaType.APPLICATION_JSON)
							.content(chaveJson("X", tecnico.getId(), null)))
					.andExpect(status().isForbidden());
			mockMvc.perform(get("/chaves-api").header("Authorization", auth))
					.andExpect(status().isForbidden());
			mockMvc.perform(delete("/chaves-api/1").header("Authorization", auth))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void rejeitaUsuarioInexistente() throws Exception {
		mockMvc.perform(post("/chaves-api")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(chaveJson("X", 999999L, null)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("Usuário")));
	}

	@Test
	void rejeitaVincularAAdmin() throws Exception {
		Usuario admin = usuarioRepository.save(UsuarioTestFactory.novo("Outro Admin",
				"admin-" + UUID.randomUUID() + "@cambia.com.br", Perfil.ADMIN));

		mockMvc.perform(post("/chaves-api")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(chaveJson("X", admin.getId(), null)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("Admin")));
	}

	@Test
	void rejeitaExpiracaoNoPassado() throws Exception {
		mockMvc.perform(post("/chaves-api")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(chaveJson("X", tecnico.getId(), LocalDate.now(FUSO).minusDays(1))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejeitaNomeEmBranco() throws Exception {
		mockMvc.perform(post("/chaves-api")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(chaveJson("   ", tecnico.getId(), null)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listaTrazStatusEDadosSemSegredo() throws Exception {
		LocalDate expira = LocalDate.now(FUSO).plusDays(30);
		Number idA = JsonPath.read(criar("Chave A", tecnico.getId(), null), "$.id");
		criar("Chave B", tecnico.getId(), expira);
		mockMvc.perform(delete("/chaves-api/" + idA).header("Authorization", adminAuth))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/chaves-api").header("Authorization", adminAuth))
				.andExpect(status().isOk())
				// mais recente primeiro
				.andExpect(jsonPath("$[0].nome").value("Chave B"))
				.andExpect(jsonPath("$[0].ativa").value(true))
				.andExpect(jsonPath("$[0].expiraEm").value(expira.toString()))
				.andExpect(jsonPath("$[0].prefixo", containsString("cambia_")))
				.andExpect(jsonPath("$[0].usuarioNome").value("Integração Teste"))
				.andExpect(jsonPath("$[0].criadaPorNome").value("Admin Chaves"))
				.andExpect(jsonPath("$[0].revogadaEm").value(nullValue()))
				.andExpect(jsonPath("$[1].nome").value("Chave A"))
				.andExpect(jsonPath("$[1].ativa").value(false))
				.andExpect(jsonPath("$[1].revogadaPorNome").value("Admin Chaves"));
	}

	@Test
	void revogarChave() throws Exception {
		Number id = JsonPath.read(criar("Chave", tecnico.getId(), null), "$.id");

		mockMvc.perform(delete("/chaves-api/" + id).header("Authorization", adminAuth))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/chaves-api/" + id).header("Authorization", adminAuth))
				.andExpect(status().isConflict());
		mockMvc.perform(delete("/chaves-api/999999").header("Authorization", adminAuth))
				.andExpect(status().isNotFound());
	}

	@Test
	void segredoNaoFicaNoBanco() throws Exception {
		String corpo = criar("Chave", tecnico.getId(), null);
		String segredo = JsonPath.read(corpo, "$.chave");
		Long id = ((Number) JsonPath.read(corpo, "$.id")).longValue();

		ChaveApi gravada = chaveApiRepository.findById(id).orElseThrow();
		assertNotEquals(segredo, gravada.getHash());
		assertEquals(GeradorDeChaveApi.hash(segredo), gravada.getHash());
	}

	@Test
	void removerUsuarioComChaveVira409() throws Exception {
		criar("Chave", tecnico.getId(), null);

		mockMvc.perform(delete("/usuarios/" + tecnico.getId()).header("Authorization", adminAuth))
				.andExpect(status().isConflict());
	}

}
