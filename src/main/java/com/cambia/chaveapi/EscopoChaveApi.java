package com.cambia.chaveapi;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Escopos de uma chave de API (Incremento 81, K4 em docs/decisoes.md). O cliente pediu
 * "não deixe o CRUD todo" (R1): uma chave só consulta e cria ordens — nunca edita, confirma
 * ou cancela ordem, nem alcança relatórios, fechamento ou cadastros. Este enum é o único
 * lugar com o mapa escopo → (método, caminho); o filtro de autenticação só pergunta aqui.
 *
 * <p>O perfil do dono continua valendo por cima (K6), no SecurityConfig: um Consultor com
 * escopo de leitura não lê clientes, porque pela tela também não leria.
 */
public enum EscopoChaveApi {

	/**
	 * GET em ordens, clientes e bancos — a integração precisa dos ids pra criar ordem. Inclui
	 * qualquer GET sob esses prefixos (histórico, consultas de CNPJ e código de banco): a lista
	 * fica fixada em ChaveApiRotasAlcancaveisTests, para uma rota nova exigir decisão
	 * consciente (pendência #24).
	 */
	LEITURA("Consultar ordens, clientes e bancos") {
		@Override
		boolean permite(String metodo, String caminho) {
			// Só GET: HEAD e OPTIONS ficam de fora de propósito (nenhuma integração precisa, e
			// o Spring atenderia HEAD com o mesmo handler do GET sem passar por aqui de novo).
			return "GET".equals(metodo)
					&& (dentroDe(caminho, "/operacoes") || dentroDe(caminho, "/clientes") || dentroDe(caminho, "/bancos"));
		}
	},

	/** Só POST /operacoes: a ordem nasce Em andamento e a confirmação é feita na tela. */
	CRIAR_ORDENS("Criar ordens") {
		@Override
		boolean permite(String metodo, String caminho) {
			return "POST".equals(metodo) && "/operacoes".equals(caminho);
		}
	};

	private final String descricao;

	EscopoChaveApi(String descricao) {
		this.descricao = descricao;
	}

	public String getDescricao() {
		return descricao;
	}

	/** @param caminho caminho já DECODIFICADO, como o Spring MVC usa para rotear. */
	abstract boolean permite(String metodo, String caminho);

	/** Algum dos escopos da chave libera esta requisição? */
	public static boolean algumPermite(Set<EscopoChaveApi> escopos, String metodo, String caminho) {
		return escopos.stream().anyMatch(escopo -> escopo.permite(metodo, caminho));
	}

	private static boolean dentroDe(String caminho, String raiz) {
		return caminho.equals(raiz) || caminho.startsWith(raiz + "/");
	}

	/** Formato gravado no banco: nomes em ordem fixa, separados por vírgula ("LEITURA,CRIAR_ORDENS"). */
	static String gravar(Collection<EscopoChaveApi> escopos) {
		// noneOf + addAll em vez de copyOf: copyOf lança exceção com coleção vazia que não seja EnumSet.
		Set<EscopoChaveApi> ordenados = EnumSet.noneOf(EscopoChaveApi.class);
		ordenados.addAll(escopos);
		return ordenados.stream().map(Enum::name).collect(Collectors.joining(","));
	}

	static Set<EscopoChaveApi> ler(String gravados) {
		Set<EscopoChaveApi> escopos = EnumSet.noneOf(EscopoChaveApi.class);
		Arrays.stream(gravados.split(",")).map(String::trim).filter(nome -> !nome.isEmpty())
				.map(EscopoChaveApi::valueOf).forEach(escopos::add);
		return escopos;
	}

	static List<String> nomes(Set<EscopoChaveApi> escopos) {
		return escopos.stream().map(Enum::name).toList();
	}

}
