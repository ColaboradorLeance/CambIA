package com.cambia.operacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cambia.banco.BancoRepository;
import com.cambia.cliente.ClienteRepository;

import org.springframework.stereotype.Service;

@Service
class RelatorioPosicaoAbertoService {

	private final OperacaoRepository operacaoRepository;
	private final ClienteRepository clienteRepository;
	private final BancoRepository bancoRepository;

	RelatorioPosicaoAbertoService(OperacaoRepository operacaoRepository, ClienteRepository clienteRepository,
			BancoRepository bancoRepository) {
		this.operacaoRepository = operacaoRepository;
		this.clienteRepository = clienteRepository;
		this.bancoRepository = bancoRepository;
	}

	PosicaoAbertoResponse calcular(LocalDate hoje) {
		List<Operacao> abertas = operacaoRepository.findByStatus(StatusOperacao.ANDAMENTO);

		List<OperacaoEmAberto> operacoes = abertas.stream()
				.map(op -> new OperacaoEmAberto(
						op.getId(),
						op.getIdTrade(),
						op.getData(),
						ChronoUnit.DAYS.between(op.getData(), hoje),
						resolverNomeCliente(op.getClienteId()),
						resolverNomeBanco(op.getBancoId()),
						op.getMoeda(),
						op.getValorMe()))
				.sorted(Comparator.comparingLong(OperacaoEmAberto::diasEmAberto).reversed())
				.toList();

		Map<String, BigDecimal> exposicaoPorMoeda = new LinkedHashMap<>();
		for (Operacao op : abertas) {
			exposicaoPorMoeda.merge(op.getMoeda(), op.getValorMe(), BigDecimal::add);
		}

		List<ExposicaoItem> exposicaoPorCliente = agruparExposicao(abertas, op -> resolverNomeCliente(op.getClienteId()));
		List<ExposicaoItem> exposicaoPorBanco = agruparExposicao(abertas, op -> resolverNomeBanco(op.getBancoId()));

		return new PosicaoAbertoResponse(abertas.size(), operacoes, exposicaoPorMoeda, exposicaoPorCliente,
				exposicaoPorBanco);
	}

	private record ChaveExposicao(String rotulo, String moeda) {
	}

	private List<ExposicaoItem> agruparExposicao(List<Operacao> abertas, Function<Operacao, String> rotuloExtrator) {
		Map<ChaveExposicao, List<Operacao>> agrupado = abertas.stream()
				.collect(Collectors.groupingBy(op -> new ChaveExposicao(rotuloExtrator.apply(op), op.getMoeda()),
						LinkedHashMap::new, Collectors.toList()));

		List<ExposicaoItem> itens = new ArrayList<>();
		for (Map.Entry<ChaveExposicao, List<Operacao>> entrada : agrupado.entrySet()) {
			BigDecimal total = entrada.getValue().stream().map(Operacao::getValorMe)
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			itens.add(new ExposicaoItem(entrada.getKey().rotulo(), entrada.getKey().moeda(),
					entrada.getValue().size(), total));
		}
		return itens;
	}

	private String resolverNomeCliente(Long clienteId) {
		return clienteRepository.findNomeById(clienteId).orElse("Cliente #" + clienteId);
	}

	private String resolverNomeBanco(Long bancoId) {
		return bancoRepository.findNomeById(bancoId).orElse("Banco #" + bancoId);
	}

}
