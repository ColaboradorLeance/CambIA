package com.cambia.web;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Garante que erros de todo tipo (não só validação de campo) vêm com uma mensagem clara
 * no corpo da resposta — não genérica ("Bad Request", "Internal Server Error") nem vazia
 * (Incremento 34/35: um erro de validação chegou a devolver 401 vazio e deslogar o usuário).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TratamentoErroGlobalTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	@Test
	void valorDeEnumInvalidoExplicaOValorRejeitado() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);

		mockMvc.perform(patch("/operacoes/999999/status")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"ARQUIVADO\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("ARQUIVADO")));
	}

	@Test
	void pathVariableInvalidoExplicaOCampoEValor() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);

		mockMvc.perform(get("/operacoes/abc").header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("id")))
				.andExpect(jsonPath("$.detail", containsString("abc")));
	}

	@Test
	void queryParamComEnumInvalidoListaOsValoresAceitos() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);

		mockMvc.perform(get("/relatorios/operacoes?periodo=XPTO").header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("HOJE")))
				.andExpect(jsonPath("$.detail", containsString("XPTO")));
	}

	@Test
	void queryParamObrigatorioFaltandoExplicaQualParametro() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);

		mockMvc.perform(get("/relatorios/operacoes").header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("periodo")));
	}

	@Test
	void jsonMalformadoNaoDevolveMensagemGenerica() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);

		String resposta = mockMvc.perform(post("/clientes")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{nome: sem aspas}"))
				.andExpect(status().isBadRequest())
				.andReturn().getResponse().getContentAsString();

		String detalhe = JsonPath.read(resposta, "$.detail");
		org.assertj.core.api.Assertions.assertThat(detalhe).isNotBlank();
		org.assertj.core.api.Assertions.assertThat(detalhe).isNotEqualTo("Failed to read request");
	}

	@Test
	void removerUsuarioComSessaoAtivaDevolveConflitoComExplicacao() throws Exception {
		// obterToken() cria o usuário e já faz login por ele, deixando uma sessão (e um
		// magic-link-token usado) vinculados ao seu id — o mesmo cenário do bug real.
		String nomeAlvo = "Analista Alvo Remoção";
		TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ANALISTA, nomeAlvo);
		Long idAlvo = usuarioRepository.findAll().stream()
				.filter(u -> u.getNome().equals(nomeAlvo))
				.findFirst().orElseThrow().getId();

		String tokenAdmin = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository,
				Perfil.ADMIN);

		mockMvc.perform(delete("/usuarios/" + idAlvo).header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail", containsString("em uso")));
	}

	@Test
	void removerClienteComOrdemVinculadaDevolveConflitoComExplicacao() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		String authHeader = "Bearer " + token;

		Long clienteId = criarERetornarId(authHeader, "/clientes",
				"{\"nome\":\"Cliente Com Ordem\",\"documento\":\"11.111.111/0001-11\"}");
		Long calculoId = criarERetornarId(authHeader, "/calculos", "{\"nome\":\"C1\",\"formula\":\"N*50%\"}");
		Long bancoId = criarERetornarId(authHeader, "/bancos",
				"""
				{"codigoBanco":"1","sigla":"B1","nome":"B1","taxaRebate":0,"calculoId":%d}
				""".formatted(calculoId));
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"data":"2026-08-01","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Pronto",
								"moeda":"USD","valorMe":100,"spotAsset":5.1,"nivelamento":5.1,"taxaFinal":5.1}
								""".formatted(clienteId, bancoId)))
				.andExpect(status().isCreated());

		mockMvc.perform(delete("/clientes/" + clienteId).header("Authorization", authHeader))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail", containsString("em uso")));
	}

	private Long criarERetornarId(String authHeader, String caminho, String json) throws Exception {
		String resposta = mockMvc.perform(post(caminho)
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	@Test
	void semTokenDevolveMensagemDeSessaoEmVezDeCorpoVazio() throws Exception {
		mockMvc.perform(get("/operacoes"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").exists())
				.andExpect(jsonPath("$.detail", containsString("login")));
	}

	@Test
	void perfilSemPermissaoDevolveMensagemDePermissaoEmVezDeCorpoVazio() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository,
				Perfil.ANALISTA);

		mockMvc.perform(get("/usuarios").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail").exists())
				.andExpect(jsonPath("$.detail", containsString("permissão")));
	}

}
