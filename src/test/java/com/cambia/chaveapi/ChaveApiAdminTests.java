package com.cambia.chaveapi;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import com.cambia.auth.TestAuthSupport.Sessao;
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

import static com.cambia.chaveapi.ChaveApiTestSupport.CRIAR_ORDENS;
import static com.cambia.chaveapi.ChaveApiTestSupport.HEADER;
import static com.cambia.chaveapi.ChaveApiTestSupport.LEITURA;
import static com.cambia.chaveapi.ChaveApiTestSupport.criarChave;
import static com.cambia.chaveapi.ChaveApiTestSupport.id;
import static com.cambia.chaveapi.ChaveApiTestSupport.segredo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremento 81 (G1–G4 de docs/chaves-api-usuario-plano.md, decisão K7): o Admin vê todas
 * as chaves, com o dono, e revoga qualquer uma (emergência: chave vazada de quem saiu ou
 * está ausente), mas não cria chave para outro usuário — cada um cria a sua (K1).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChaveApiAdminTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private Sessao adm;
	private Sessao ana;
	private Sessao caio;

	@BeforeEach
	void preparar() throws Exception {
		adm = TestAuthSupport.obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN, "Adm");
		ana = TestAuthSupport.obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ANALISTA, "Ana");
		caio = TestAuthSupport.obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.CONSULTOR, "Caio");
	}

	@Test
	void adminListaTodasComDono() throws Exception {
		long daAna = id(criarChave(mockMvc, ana.bearer(), "Da Ana", null, LEITURA, CRIAR_ORDENS));
		long doCaio = id(criarChave(mockMvc, caio.bearer(), "Do Caio", null, LEITURA));

		String lista = mockMvc.perform(get("/chaves-api").header("Authorization", adm.bearer()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertEquals(java.util.List.of("Ana"), JsonPath.read(lista, "$[?(@.id == %d)].donoNome".formatted(daAna)));
		assertEquals(java.util.List.of("Caio"), JsonPath.read(lista, "$[?(@.id == %d)].donoNome".formatted(doCaio)));
		java.util.List<java.util.List<String>> escopos = JsonPath.read(lista, "$[?(@.id == %d)].escopos".formatted(daAna));
		assertEquals(java.util.List.of(java.util.List.of(LEITURA, CRIAR_ORDENS)), escopos);
	}

	@Test
	void adminRevogaQualquerUma() throws Exception {
		String corpo = criarChave(mockMvc, ana.bearer(), "Da Ana", null, LEITURA);

		mockMvc.perform(delete("/chaves-api/" + id(corpo)).header("Authorization", adm.bearer()))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/operacoes").header(HEADER, segredo(corpo)))
				.andExpect(status().isUnauthorized());

		String lista = mockMvc.perform(get("/chaves-api").header("Authorization", adm.bearer()))
				.andReturn().getResponse().getContentAsString();
		assertEquals(java.util.List.of("Adm"), JsonPath.read(lista, "$[?(@.id == %d)].revogadaPorNome".formatted(id(corpo))));
		// A dona vê que a chave foi revogada (e por quem, na tela do Admin).
		mockMvc.perform(get("/perfil/chaves-api").header("Authorization", ana.bearer()))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].status").value("REVOGADA"));
	}

	@Test
	void analistaNaoUsaRotaDoAdmin() throws Exception {
		long daAna = id(criarChave(mockMvc, ana.bearer(), "Da Ana", null, LEITURA));

		for (Sessao sessao : new Sessao[] { ana, caio }) {
			mockMvc.perform(get("/chaves-api").header("Authorization", sessao.bearer()))
					.andExpect(status().isForbidden());
			mockMvc.perform(delete("/chaves-api/" + daAna).header("Authorization", sessao.bearer()))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void adminNaoCriaParaOutros() throws Exception {
		// K7: a criação saiu de /chaves-api; cada um cria a própria em /perfil/chaves-api.
		mockMvc.perform(post("/chaves-api")
						.header("Authorization", adm.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Para a Ana\",\"usuarioId\":%d,\"escopos\":[\"LEITURA\"]}"
								.formatted(ana.usuario().getId())))
				.andExpect(status().isMethodNotAllowed());
	}

}
