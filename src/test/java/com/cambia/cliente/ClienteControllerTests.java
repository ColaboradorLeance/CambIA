package com.cambia.cliente;

import java.util.Optional;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClienteControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	// Substitui o cliente real (que chamaria a BrasilAPI de verdade pela internet) por
	// um dublê — os testes não podem depender de rede externa.
	@MockitoBean
	private ConsultaCnpjClient consultaCnpjClient;

	private String authHeader;

	private static final String CLIENTE_JSON = """
			{"nome":"CRAS Agroindustria LTDA","documento":"14.777.639/0001-92"}
			""";

	@BeforeEach
	void autenticarComoAdmin() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		authHeader = "Bearer " + token;
	}

	@Test
	void criaEBuscaCliente() throws Exception {
		String location = mockMvc.perform(post("/clientes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(CLIENTE_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.nome").value("CRAS Agroindustria LTDA"))
				.andExpect(jsonPath("$.documento").value("14.777.639/0001-92"))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(get(location).header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value("CRAS Agroindustria LTDA"));
	}

	@Test
	void listaClientesCadastrados() throws Exception {
		mockMvc.perform(post("/clientes")
				.header("Authorization", authHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.content(CLIENTE_JSON));

		mockMvc.perform(get("/clientes").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$[?(@.nome=='CRAS Agroindustria LTDA')]").exists());
	}

	@Test
	void atualizaCliente() throws Exception {
		String location = mockMvc.perform(post("/clientes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(CLIENTE_JSON))
				.andReturn().getResponse().getHeader("Location");

		String atualizado = """
				{"nome":"CRAS Agroindustria LTDA","documento":"14.777.639/0001-93"}
				""";

		mockMvc.perform(put(location)
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(atualizado))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.documento").value("14.777.639/0001-93"));

		mockMvc.perform(get(location).header("Authorization", authHeader))
				.andExpect(jsonPath("$.documento").value("14.777.639/0001-93"));
	}

	@Test
	void removeCliente() throws Exception {
		String location = mockMvc.perform(post("/clientes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(CLIENTE_JSON))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(delete(location).header("Authorization", authHeader))
				.andExpect(status().isNoContent());

		mockMvc.perform(get(location).header("Authorization", authHeader))
				.andExpect(status().isNotFound());
	}

	@Test
	void retorna404ParaClienteInexistente() throws Exception {
		mockMvc.perform(get("/clientes/999999").header("Authorization", authHeader))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejeitaCadastroSemNome() throws Exception {
		String semNome = """
				{"nome":"","documento":"14.777.639/0001-92"}
				""";

		mockMvc.perform(post("/clientes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(semNome))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejeitaCadastroSemDocumento() throws Exception {
		String semDocumento = """
				{"nome":"CRAS Agroindustria LTDA","documento":""}
				""";

		mockMvc.perform(post("/clientes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(semDocumento))
				.andExpect(status().isBadRequest());
	}

	@Test
	void consultaCnpjRetornaONomeEncontrado() throws Exception {
		when(consultaCnpjClient.buscarNome("14777639000192")).thenReturn(Optional.of("CRAS Agroindustria LTDA"));

		mockMvc.perform(get("/clientes/consulta-cnpj/14777639000192").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value("CRAS Agroindustria LTDA"));
	}

	@Test
	void consultaCnpjRetornaNomeNuloQuandoNaoEncontrado() throws Exception {
		when(consultaCnpjClient.buscarNome(eq("14777639000192"))).thenReturn(Optional.empty());

		mockMvc.perform(get("/clientes/consulta-cnpj/14777639000192").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void consultaCnpjNaoChamaAFonteDeDadosQuandoNaoTem14Digitos() throws Exception {
		mockMvc.perform(get("/clientes/consulta-cnpj/123.456.789-00").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value(org.hamcrest.Matchers.nullValue()));

		verifyNoInteractions(consultaCnpjClient);
	}

}
