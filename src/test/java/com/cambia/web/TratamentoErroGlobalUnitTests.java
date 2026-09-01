package com.cambia.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Achado de revisão de segurança: o handler de erro genérico devolvia ex.getMessage()
 * cru pro cliente — qualquer exceção não mapeada (conexão de banco, IO, etc.) podia
 * vazar detalhe interno (host, caminho, fragmento de configuração). Teste de unidade
 * direto na classe (sem contexto Spring) porque não existe um caminho real no app hoje
 * que dispare uma exceção genuinamente não mapeada — o app já trata os erros esperados
 * especificamente; este handler só existe pra bugs de verdade.
 */
class TratamentoErroGlobalUnitTests {

	@Test
	void erroInesperadoNuncaVazaAMensagemOriginalDaExcecao() {
		TratamentoErroGlobal tratamento = new TratamentoErroGlobal();
		RuntimeException erroComDetalheSensivel = new RuntimeException(
				"Connection refused: host=db-interno.local senha=segredo123");

		ResponseEntity<Object> resposta = tratamento.tratarErroInesperado(erroComDetalheSensivel,
				new ServletWebRequest(new MockHttpServletRequest()));

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resposta.getStatusCode());
		ProblemDetail corpo = (ProblemDetail) resposta.getBody();
		assertFalse(corpo.getDetail().contains("segredo123"));
		assertFalse(corpo.getDetail().contains("db-interno"));
		assertEquals("Erro inesperado no servidor. Tente novamente ou contate o suporte.", corpo.getDetail());
	}

}
