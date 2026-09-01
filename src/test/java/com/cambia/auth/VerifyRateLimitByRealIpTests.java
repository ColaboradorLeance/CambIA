package com.cambia.auth;

import com.cambia.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Achado de revisão de segurança, confirmado ao vivo antes da correção: atrás do
 * reverse-proxy, {@code getRemoteAddr()} sempre devolvia o IP do container do proxy —
 * nunca o do cliente de verdade — fazendo o limite de tentativas de {@code /auth/verify}
 * virar, na prática, um único limite compartilhado por todo mundo. Corrigido com
 * {@link ResolvedorDeIpReal} (ver a classe pra detalhes de por que não usei o mecanismo
 * nativo do Spring/Tomcat).
 *
 * <p>Simula dois "clientes" diferentes atrás do "mesmo proxy": mesmo
 * {@code getRemoteAddr()} simulado (representando o container do reverse-proxy), mas
 * {@code X-Forwarded-For} diferente em cada um.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "cambia.rate-limit.auth.maximo=3")
class VerifyRateLimitByRealIpTests {

	private static final String IP_DO_PROXY = "172.18.0.5"; // simula o container reverse-proxy

	@Autowired
	private MockMvc mockMvc;

	@Test
	void duasOrigensAtrasDoMesmoProxyTemLimitesIndependentes() throws Exception {
		esgotarLimite("203.0.113.10");

		// Achado corrigido: uma origem diferente (outro X-Forwarded-For, mesmo
		// getRemoteAddr() simulado do proxy) não deveria compartilhar o limite já
		// esgotado da primeira.
		mockMvc.perform(tentarVerify("203.0.113.20"))
				.andExpect(status().isUnauthorized());
	}

	private void esgotarLimite(String ipReal) throws Exception {
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(tentarVerify(ipReal));
		}
		mockMvc.perform(tentarVerify(ipReal))
				.andExpect(status().isTooManyRequests());
	}

	private MockHttpServletRequestBuilder tentarVerify(String ipReal) {
		return get("/auth/verify")
				.param("token", "token-que-nao-existe")
				.header("X-Forwarded-For", ipReal)
				.with(request -> {
					request.setRemoteAddr(IP_DO_PROXY);
					return request;
				});
	}

}
