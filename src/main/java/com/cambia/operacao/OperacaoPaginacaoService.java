package com.cambia.operacao;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.cambia.banco.BancoRepository;
import com.cambia.cliente.ClienteRepository;
import com.cambia.usuario.UsuarioRepository;

import jakarta.persistence.criteria.Predicate;

import org.hibernate.query.criteria.JpaExpression;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Listagem paginada e filtrada de ordens (Incremento 80). Antes, as telas baixavam a
 * tabela inteira e filtravam no navegador — custo linear e crescente com o histórico (no
 * teste de carga, 914 ordens já faziam a listagem cair de 200 pra 42 req/s).
 *
 * <p>Duas escolhas de desempenho:
 * <ul>
 * <li>Só entra na consulta o predicado do filtro preenchido. O atalho "(:x IS NULL OR
 * coluna = :x)" deixaria o PostgreSQL com um plano genérico que não usa índice.</li>
 * <li>Filtros por nome (cliente, banco, usuário) são resolvidos antes, em ids, nas tabelas
 * pequenas; a consulta principal só faz "IN (ids)" no índice de operacoes. Nome que não
 * casa com nada devolve a página vazia sem consultar operacoes.</li>
 * </ul>
 */
@Service
class OperacaoPaginacaoService {

	static final int TAMANHO_MAXIMO = 200;
	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	private final OperacaoRepository repository;
	private final ClienteRepository clienteRepository;
	private final BancoRepository bancoRepository;
	private final UsuarioRepository usuarioRepository;
	private final OperacaoService operacaoService;

	OperacaoPaginacaoService(OperacaoRepository repository, ClienteRepository clienteRepository,
			BancoRepository bancoRepository, UsuarioRepository usuarioRepository, OperacaoService operacaoService) {
		this.repository = repository;
		this.clienteRepository = clienteRepository;
		this.bancoRepository = bancoRepository;
		this.usuarioRepository = usuarioRepository;
		this.operacaoService = operacaoService;
	}

	PaginaResponse<OperacaoResponse> paginar(FiltroOperacoes filtro, int pagina, int tamanho) {
		if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Página deve ser 0 ou mais e tamanho entre 1 e " + TAMANHO_MAXIMO);
		}

		List<Specification<Operacao>> condicoes = new ArrayList<>();

		// Filtros por nome primeiro: se algum não casa com nada, nem consulta operacoes.
		if (!restringirPorIds(condicoes, "clienteId", filtro.nome(), clienteRepository::findIdsPorNome)
				|| !restringirPorIds(condicoes, "clienteId", filtro.cnpj(), clienteRepository::findIdsPorDocumento)
				|| !restringirPorIds(condicoes, "bancoId", filtro.banco(), bancoRepository::findIdsPorNome)
				|| !restringirPorIds(condicoes, "criadoPorUsuarioId", filtro.criadoPor(),
						usuarioRepository::findIdsPorNome)
				|| !restringirPorIds(condicoes, "completadoPorUsuarioId", filtro.completadoPor(),
						usuarioRepository::findIdsPorNome)) {
			return PaginaResponse.vazia(pagina, tamanho);
		}

		if (filtro.status() != null) {
			condicoes.add((raiz, consulta, cb) -> cb.equal(raiz.get("status"), filtro.status()));
		}
		igual(condicoes, "moeda", filtro.moeda());
		igual(condicoes, "cv", filtro.cv());
		igual(condicoes, "prCrVir", filtro.tipo());
		igual(condicoes, "fundo", filtro.fundo() == null ? null : filtro.fundo().toUpperCase(Locale.ROOT));

		String idTrade = padraoContem(filtro.idTrade());
		if (idTrade != null) {
			condicoes.add((raiz, consulta, cb) -> cb.like(cb.lower(raiz.get("idTrade")), idTrade, '\\'));
		}

		String valor = padraoDeValor(filtro.valorMoeda());
		if (valor != null) {
			// JpaExpression.cast gera CAST(... AS varchar) no SQL; o .as(String.class) do JPA
			// só muda o tipo no Java e o PostgreSQL recusa "numeric LIKE text".
			condicoes.add((raiz, consulta, cb) -> cb.like(
					((JpaExpression<?>) raiz.get("valorMe")).cast(String.class), valor));
		}

		// Intervalo [início, fim) do dia em São Paulo, em vez de converter a coluna: assim a
		// comparação continua utilizável pelo índice (status, completado_em).
		if (filtro.dataFechamento() != null) {
			Instant inicio = filtro.dataFechamento().atStartOfDay(FUSO).toInstant();
			Instant fim = filtro.dataFechamento().plusDays(1).atStartOfDay(FUSO).toInstant();
			condicoes.add((raiz, consulta, cb) -> cb.and(
					cb.greaterThanOrEqualTo(raiz.get("completadoEm"), inicio),
					cb.lessThan(raiz.get("completadoEm"), fim)));
		}

		Specification<Operacao> especificacao = (raiz, consulta, cb) -> cb.and(condicoes.stream()
				.map(condicao -> condicao.toPredicate(raiz, consulta, cb))
				.toArray(Predicate[]::new));

		Page<Operacao> resultado = repository.findAll(especificacao,
				PageRequest.of(pagina, tamanho, ordenacao(filtro.status())));

		return new PaginaResponse<>(operacaoService.toResponses(resultado.getContent()), resultado.getTotalElements(),
				pagina, tamanho, resultado.getTotalPages());
	}

	OpcoesFiltroResponse opcoesDeFiltro(StatusOperacao status) {
		return new OpcoesFiltroResponse(repository.moedasDistintas(status), repository.fundosDistintos(status));
	}

	// Confirmadas: a mais recentemente confirmada primeiro (é a que o usuário procura);
	// casa com o índice (status, completado_em DESC, id DESC). Demais: a mais nova primeiro.
	private static Sort ordenacao(StatusOperacao status) {
		if (status == StatusOperacao.CONFIRMADO) {
			return Sort.by(Sort.Order.desc("completadoEm"), Sort.Order.desc("id"));
		}
		return Sort.by(Sort.Order.desc("id"));
	}

	/** @return {@code false} se o texto foi informado e não casa com nenhum registro. */
	private static boolean restringirPorIds(List<Specification<Operacao>> condicoes, String atributo, String texto,
			Function<String, List<Long>> buscarIds) {
		String padrao = padraoContem(texto);
		if (padrao == null) {
			return true;
		}
		List<Long> ids = buscarIds.apply(padrao);
		if (ids.isEmpty()) {
			return false;
		}
		condicoes.add((raiz, consulta, cb) -> raiz.get(atributo).in(ids));
		return true;
	}

	private static void igual(List<Specification<Operacao>> condicoes, String atributo, String valor) {
		if (valor != null && !valor.isBlank()) {
			String limpo = valor.trim();
			condicoes.add((raiz, consulta, cb) -> cb.equal(raiz.get(atributo), limpo));
		}
	}

	/**
	 * "contém", sem diferenciar caixa. % e _ digitados viram texto literal (escape com
	 * barra invertida): sem isso, buscar "%" casaria com tudo.
	 */
	static String padraoContem(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		String escapado = texto.trim().toLowerCase(Locale.ROOT)
				.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
		return "%" + escapado + "%";
	}

	/**
	 * O usuário digita o valor como a tela mostra, em formato brasileiro ("1.234,56"); a
	 * coluna vira texto em formato SQL ("1234.56"). Ponto é separador de milhar (some),
	 * vírgula é o decimal (vira ponto). Fica só dígito e ponto — nada que vire curinga.
	 */
	static String padraoDeValor(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		String normalizado = texto.trim().replace(".", "").replace(",", ".").replaceAll("[^0-9.]", "");
		return normalizado.isEmpty() ? null : "%" + normalizado + "%";
	}

}
