package com.cambia.banco;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BrasilApiConsultaBancoClientTests {

	private static final String BASE_URL = "https://brasilapi.com.br/api/banks/v1";

	private BrasilApiConsultaBancoClient client;
	private MockRestServiceServer server;

	private void preparar() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new BrasilApiConsultaBancoClient(builder);
	}

	@Test
	void buscaPorCompeRetornaONomeCompletoQuandoEncontrado() {
		preparar();
		server.expect(requestTo(BASE_URL + "/001"))
				.andRespond(withSuccess(
						"{\"ispb\":\"00000000\",\"name\":\"BCO DO BRASIL S.A.\",\"code\":1,\"fullName\":\"Banco do Brasil S.A.\"}",
						MediaType.APPLICATION_JSON));

		assertEquals(Optional.of("Banco do Brasil S.A."), client.buscarNomePorCompe("001"));
	}

	@Test
	void buscaPorCompeUsaNomeAbreviadoQuandoNaoHaNomeCompleto() {
		preparar();
		server.expect(requestTo(BASE_URL + "/001"))
				.andRespond(withSuccess("{\"ispb\":\"00000000\",\"name\":\"BCO DO BRASIL S.A.\",\"code\":1,\"fullName\":null}",
						MediaType.APPLICATION_JSON));

		assertEquals(Optional.of("BCO DO BRASIL S.A."), client.buscarNomePorCompe("001"));
	}

	@Test
	void buscaPorCompeRetornaVazioQuandoNaoParticipaDoCompe() {
		preparar();
		server.expect(requestTo(BASE_URL + "/999")).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertTrue(client.buscarNomePorCompe("999").isEmpty());
	}

	@Test
	void buscaPorCompeRetornaVazioQuandoAApiFalha() {
		preparar();
		server.expect(requestTo(BASE_URL + "/001")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		assertTrue(client.buscarNomePorCompe("001").isEmpty());
	}

	@Test
	void buscaPorIspbEncontraNaListaCompleta() {
		preparar();
		server.expect(requestTo(BASE_URL))
				.andRespond(withSuccess("""
						[
							{"ispb":"00000000","name":"BCO DO BRASIL S.A.","code":1,"fullName":"Banco do Brasil S.A."},
							{"ispb":"60701190","name":"ITAU UNIBANCO S.A.","code":341,"fullName":"ITAÚ UNIBANCO S.A."}
						]
						""", MediaType.APPLICATION_JSON));

		assertEquals(Optional.of("ITAÚ UNIBANCO S.A."), client.buscarNomePorIspb("60701190"));
	}

	@Test
	void buscaPorIspbRetornaVazioQuandoNaoEstaNaLista() {
		preparar();
		server.expect(requestTo(BASE_URL))
				.andRespond(withSuccess(
						"[{\"ispb\":\"00000000\",\"name\":\"BCO DO BRASIL S.A.\",\"code\":1,\"fullName\":\"Banco do Brasil S.A.\"}]",
						MediaType.APPLICATION_JSON));

		assertTrue(client.buscarNomePorIspb("99999999").isEmpty());
	}

	@Test
	void buscaPorIspbRetornaVazioQuandoAApiFalha() {
		preparar();
		server.expect(requestTo(BASE_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		assertTrue(client.buscarNomePorIspb("60701190").isEmpty());
	}

}
