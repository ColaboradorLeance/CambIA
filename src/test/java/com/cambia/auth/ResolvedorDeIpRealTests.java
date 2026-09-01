package com.cambia.auth;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResolvedorDeIpRealTests {

	@Test
	void semXForwardedForUsaOEnderecoDaConexaoDireta() {
		HttpServletRequest request = requisicao("203.0.113.5", null);

		assertEquals("203.0.113.5", ResolvedorDeIpReal.resolver(request));
	}

	@Test
	void confiaNoHeaderQuandoQuemConectouEUmProxyInterno() {
		// 172.18.0.5 é uma faixa típica de rede interna do Docker Compose
		HttpServletRequest request = requisicao("172.18.0.5", "198.51.100.7");

		assertEquals("198.51.100.7", ResolvedorDeIpReal.resolver(request));
	}

	@Test
	void ignoraOHeaderQuandoQuemConectouNaoEUmProxyConfiavel() {
		// Alguém de fora tentando forjar o header direto, sem passar pelo reverse-proxy
		// (só seria possível se o backend estivesse exposto direto — não está, mas o
		// código não deveria confiar cegamente mesmo assim).
		HttpServletRequest request = requisicao("203.0.113.99", "9.9.9.9");

		assertEquals("203.0.113.99", ResolvedorDeIpReal.resolver(request));
	}

	@Test
	void pegaOUltimoEnderecoNaoInternoAndandoDaDireitaParaEsquerda() {
		// Simula o nginx recebendo uma requisição já com um X-Forwarded-For forjado
		// ("9.9.9.9") e anexando o IP de quem conectou nele de verdade
		// ($proxy_add_x_forwarded_for) — o valor forjado deve ser ignorado.
		HttpServletRequest request = requisicao("172.18.0.5", "9.9.9.9, 203.0.113.42");

		assertEquals("203.0.113.42", ResolvedorDeIpReal.resolver(request));
	}

	@Test
	void doisSaltosDeProxyInternosAindaAchaOClienteDeVerdade() {
		HttpServletRequest request = requisicao("172.18.0.5", "203.0.113.1, 10.0.0.2, 172.20.0.3");

		assertEquals("203.0.113.1", ResolvedorDeIpReal.resolver(request));
	}

	private HttpServletRequest requisicao(String remoteAddr, String xForwardedFor) {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRemoteAddr()).thenReturn(remoteAddr);
		when(request.getHeader("X-Forwarded-For")).thenReturn(xForwardedFor);
		return request;
	}

}
