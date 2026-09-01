package com.cambia.cliente;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BrasilApiConsultaCnpjClientTests {

	private static final String CNPJ = "11222333000181";
	private static final String URL = "https://brasilapi.com.br/api/cnpj/v1/" + CNPJ;

	private BrasilApiConsultaCnpjClient client;
	private MockRestServiceServer server;

	private void preparar() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new BrasilApiConsultaCnpjClient(builder);
	}

	@Test
	void retornaARazaoSocialQuandoAApiResponde() {
		preparar();
		server.expect(requestTo(URL))
				.andRespond(withSuccess("{\"razao_social\":\"ACME COMERCIO LTDA\",\"nome_fantasia\":\"Acme\"}",
						MediaType.APPLICATION_JSON));

		assertEquals(Optional.of("ACME COMERCIO LTDA"), client.buscarNome(CNPJ));
	}

	@Test
	void usaNomeFantasiaQuandoNaoHaRazaoSocial() {
		preparar();
		server.expect(requestTo(URL))
				.andRespond(withSuccess("{\"razao_social\":null,\"nome_fantasia\":\"Acme\"}", MediaType.APPLICATION_JSON));

		assertEquals(Optional.of("Acme"), client.buscarNome(CNPJ));
	}

	@Test
	void retornaVazioQuandoCnpjNaoEExiste() {
		preparar();
		server.expect(requestTo(URL)).andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

		assertTrue(client.buscarNome(CNPJ).isEmpty());
	}

	@Test
	void retornaVazioQuandoAApiFalha() {
		preparar();
		server.expect(requestTo(URL)).andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

		assertTrue(client.buscarNome(CNPJ).isEmpty());
	}

	@Test
	void retornaVazioQuandoNaoHaRazaoSocialNemNomeFantasia() {
		preparar();
		server.expect(requestTo(URL)).andRespond(withSuccess("{\"razao_social\":null}", MediaType.APPLICATION_JSON));

		assertTrue(client.buscarNome(CNPJ).isEmpty());
	}

}
