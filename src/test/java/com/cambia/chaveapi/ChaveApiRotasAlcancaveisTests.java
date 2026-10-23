package com.cambia.chaveapi;

import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

import com.cambia.TestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Incremento 81 (achado da revisão de segurança): o escopo LEITURA libera QUALQUER GET sob
 * /operacoes, /clientes e /bancos. Uma rota nova nesses prefixos passaria a ser alcançável
 * por chave sem ninguém decidir. Este teste fixa a lista: se mudar, ele falha, e a rota
 * nova precisa ser aceita aqui de propósito (ou ficar fora do escopo). Pendência #24.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ChaveApiRotasAlcancaveisTests {

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mapeamentos;

	@Test
	void rotasAlcancaveisPorChaveSaoAsEsperadas() {
		Set<String> alcancaveis = new TreeSet<>();
		Set<EscopoChaveApi> todos = EnumSet.allOf(EscopoChaveApi.class);
		mapeamentos.getHandlerMethods().keySet().forEach(info -> {
			Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();
			for (String padrao : info.getPatternValues()) {
				// Variável de caminho vira um valor concreto, como numa chamada real.
				String caminho = padrao.replaceAll("\\{[^}]+}", "1");
				for (RequestMethod metodo : metodos) {
					if (EscopoChaveApi.algumPermite(todos, metodo.name(), caminho)) {
						alcancaveis.add(metodo.name() + " " + padrao);
					}
				}
			}
		});

		assertEquals(new TreeSet<>(Set.of(
				"GET /bancos",
				"GET /bancos/consulta-codigo/{codigo}",
				"GET /bancos/{id}",
				"GET /clientes",
				"GET /clientes/consulta-cnpj/{cnpj}",
				"GET /clientes/{id}",
				"GET /operacoes",
				"GET /operacoes/historico",
				"GET /operacoes/opcoes-filtro",
				"GET /operacoes/pagina",
				"GET /operacoes/{id}",
				"POST /operacoes")), alcancaveis);
	}

}
