package com.cambia.operacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cambia.banco.BancoRepository;
import com.cambia.cliente.ClienteRepository;
import com.cambia.exportacao.ColunaRelatorio;
import com.cambia.exportacao.Destaque;
import com.cambia.exportacao.FormatoBrasileiro;
import com.cambia.exportacao.RelatorioTabular;
import com.cambia.exportacao.SecaoRelatorio;
import com.cambia.exportacao.TipoColuna;
import com.cambia.usuario.Usuario;
import com.cambia.usuario.UsuarioRepository;

import org.springframework.stereotype.Service;

import static com.cambia.exportacao.ColunaRelatorio.de;
import static com.cambia.exportacao.ColunaRelatorio.soExcel;

/**
 * Converte cada relatório no modelo neutro de exportação (Incremento 82). Os dados vêm
 * exatamente dos mesmos serviços das telas — a exportação nunca recalcula nada por conta
 * própria, então o arquivo não tem como divergir do que o usuário está vendo.
 *
 * <p>Nomenclatura dos campos de ordem: a do documento do cliente (Incremento 73), a mesma
 * das telas de Ordens.
 */
@Service
class RelatorioExportacaoService {

	private static final Map<StatusOperacao, String> ROTULO_STATUS = Map.of(
			StatusOperacao.ANDAMENTO, "Em andamento",
			StatusOperacao.CONFIRMADO, "Confirmado",
			StatusOperacao.CANCELADO, "Cancelado");

	private static final List<ColunaRelatorio> COLUNAS_ORDEM = List.of(
			de("Código da Ordem", TipoColuna.TEXTO),
			soExcel("Código Operação Origem", TipoColuna.TEXTO),
			de("Data do Fechamento", TipoColuna.DATA),
			de("Cliente", TipoColuna.TEXTO),
			soExcel("CNPJ/CPF", TipoColuna.TEXTO),
			de("Banco", TipoColuna.TEXTO),
			soExcel("Modelo de cálculo", TipoColuna.TEXTO),
			de("C/V", TipoColuna.TEXTO),
			de("Tipo Ordem", TipoColuna.TEXTO),
			de("Fundo", TipoColuna.TEXTO),
			de("Moeda", TipoColuna.TEXTO),
			de("Valor em moeda", TipoColuna.DECIMAL),
			soExcel("Spot Asset", TipoColuna.TAXA),
			soExcel("Nivelamento", TipoColuna.TAXA),
			soExcel("Taxa Final", TipoColuna.TAXA),
			de("Status", TipoColuna.STATUS),
			de("Valor em Real", TipoColuna.DECIMAL).somada(),
			soExcel("Total Bruto Câmbio", TipoColuna.DECIMAL).somada(),
			de("Valor Absoluto", TipoColuna.DECIMAL).somada(),
			soExcel("Spread emissão", TipoColuna.AUTO),
			soExcel("Spread liquidação", TipoColuna.PERCENTUAL),
			soExcel("Custo", TipoColuna.PERCENTUAL),
			soExcel("Rebate", TipoColuna.DECIMAL3),
			soExcel("Base de comissionamento", TipoColuna.DECIMAL3),
			de("Comissão Líquida", TipoColuna.DECIMAL).somada(),
			de("Criado por", TipoColuna.TEXTO),
			soExcel("Criado em", TipoColuna.DATA_HORA),
			de("Completado por", TipoColuna.TEXTO),
			soExcel("Completado em", TipoColuna.DATA_HORA));

	private final ClienteRepository clienteRepository;
	private final BancoRepository bancoRepository;
	private final UsuarioRepository usuarioRepository;

	RelatorioExportacaoService(ClienteRepository clienteRepository, BancoRepository bancoRepository,
			UsuarioRepository usuarioRepository) {
		this.clienteRepository = clienteRepository;
		this.bancoRepository = bancoRepository;
		this.usuarioRepository = usuarioRepository;
	}

	// ------------------------------------------------------------------ Ordens

	RelatorioTabular ordens(List<OperacaoResponse> ordens, IntervaloDatas intervalo, FiltrosRelatorioOrdens filtros) {
		List<String> cabecalho = new ArrayList<>();
		cabecalho.add("Período: " + FormatoBrasileiro.intervalo(intervalo.inicio(), intervalo.fim()));
		String descricaoFiltros = descreverFiltros(filtros);
		if (!descricaoFiltros.isEmpty()) {
			cabecalho.add("Filtros: " + descricaoFiltros);
		}
		cabecalho.add(geradoEm());

		// Cartões: dinheiro só das CONFIRMADAS, a mesma semântica de Fechamento/Rankings
		// (Incremento 76) — a prévia das em andamento não infla o número principal. A linha
		// de total da tabela soma o que está listado; no Excel ela acompanha o filtro.
		List<OperacaoResponse> confirmadas = ordens.stream()
				.filter(op -> op.status() == StatusOperacao.CONFIRMADO)
				.toList();
		List<Destaque> destaques = List.of(
				new Destaque("Ordens no período", ordens.size(), TipoColuna.INTEIRO),
				new Destaque("Confirmadas", confirmadas.size(), TipoColuna.INTEIRO),
				new Destaque("Valor em Real (confirmadas)", somar(confirmadas, OperacaoResponse::reais), TipoColuna.DECIMAL),
				new Destaque("Comissão Líquida (confirmadas)", somar(confirmadas, OperacaoResponse::comissaoLiquida),
						TipoColuna.DECIMAL));

		List<List<Object>> linhas = ordens.stream().map(RelatorioExportacaoService::linhaOrdem).toList();
		return new RelatorioTabular("Relatório de Ordens", "relatorio-ordens-" + intervalo.inicio() + "-a-" + intervalo.fim(),
				cabecalho, destaques, List.of(new SecaoRelatorio("Ordens", COLUNAS_ORDEM, linhas)));
	}

	private static List<Object> linhaOrdem(OperacaoResponse op) {
		boolean credito = "Credito".equals(op.prCrVir());
		return Arrays.asList(
				op.idTrade(), op.codigoOperacao(), op.data(), op.clienteNome(), op.clienteDocumento(), op.bancoNome(),
				op.calculoNome(),
				op.cv(), op.prCrVir(), op.fundo(), op.moeda(), op.valorMe(), op.spotAsset(), op.nivelamento(),
				op.taxaFinal(), ROTULO_STATUS.get(op.status()), op.reais(), op.totalBrutoCambio(), op.valorAbsoluto(),
				spreadEmissao(op.spreadEmissao()), op.spreadLiquidacao(),
				// Mesma regra das telas (Incremento 74): Custo só é informado em Crédito.
				credito ? op.custo() : null,
				op.rebate(), op.baseComissionamento(), op.comissaoLiquida(), op.criadoPorNome(), op.criadoEm(),
				op.completadoPorNome(), op.completadoEm());
	}

	private static BigDecimal somar(List<OperacaoResponse> ordens,
			java.util.function.Function<OperacaoResponse, BigDecimal> campo) {
		return ordens.stream().map(campo).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	/** "NA" fica texto; um número vira percentual, como na tela. */
	private static Object spreadEmissao(String valor) {
		if (valor == null || "NA".equalsIgnoreCase(valor)) {
			return valor;
		}
		return FormatoBrasileiro.formatar(new BigDecimal(valor), TipoColuna.PERCENTUAL);
	}

	private String descreverFiltros(FiltrosRelatorioOrdens f) {
		List<String> partes = new ArrayList<>();
		if (f.clienteId() != null) {
			partes.add("cliente " + clienteRepository.findNomeById(f.clienteId()).orElse("#" + f.clienteId()));
		}
		if (f.bancoId() != null) {
			partes.add("banco " + bancoRepository.findNomeById(f.bancoId()).orElse("#" + f.bancoId()));
		}
		if (f.moeda() != null) {
			partes.add("moeda " + f.moeda());
		}
		if (f.cv() != null) {
			partes.add("C/V " + f.cv());
		}
		if (f.status() != null) {
			partes.add("status " + ROTULO_STATUS.get(f.status()));
		}
		if (f.criadoPorUsuarioId() != null) {
			partes.add("criado por " + nomeUsuario(f.criadoPorUsuarioId()));
		}
		if (f.completadoPorUsuarioId() != null) {
			partes.add("completado por " + nomeUsuario(f.completadoPorUsuarioId()));
		}
		return String.join(", ", partes);
	}

	private String nomeUsuario(Long id) {
		return usuarioRepository.findById(id).map(Usuario::getNome).orElse("#" + id);
	}

	// ------------------------------------------------------------------ Comparativo

	RelatorioTabular comparativo(ComparativoResponse c) {
		// Sem "periodo", a comparação foi entre dois intervalos livres (A e B), não com o
		// período anterior equivalente — os rótulos acompanham, como na tela.
		boolean doisPeriodos = c.periodo() == null;
		String rotuloPrincipal = doisPeriodos ? "Período A" : "Período";
		String rotuloComparado = doisPeriodos ? "Período B" : "Período anterior";
		PeriodoComparado anterior = c.periodoAnterior();

		List<List<Object>> resumo = new ArrayList<>();
		resumo.add(linha(rotuloPrincipal, FormatoBrasileiro.intervalo(c.inicio(), c.fim())));
		resumo.add(linha("Total de ordens", c.totalOperacoes()));
		resumo.add(linha("Total em reais", c.totalReais()));
		resumo.add(linha("Comissão líquida", c.totalComissaoLiquida()));
		resumo.add(linha("Média diária de comissão", c.mediaDiariaComissaoLiquida()));
		resumo.add(linha("Melhor dia", descreverDia(c.melhorDia())));
		resumo.add(linha("Pior dia", descreverDia(c.piorDia())));
		resumo.add(linha(rotuloComparado, FormatoBrasileiro.intervalo(anterior.inicio(), anterior.fim())));
		resumo.add(linha("Total de ordens — " + rotuloComparado.toLowerCase(), anterior.totalOperacoes()));
		resumo.add(linha("Total em reais — " + rotuloComparado.toLowerCase(), anterior.totalReais()));
		resumo.add(linha("Comissão líquida — " + rotuloComparado.toLowerCase(), anterior.totalComissaoLiquida()));
		resumo.add(linha("Variação da comissão", variacao(c.variacaoComissaoLiquidaPercentual())));

		// Só dias com movimento, como a tela: o serviço devolve TODOS os dias do período
		// (zerados inclusive, pra média diária) — um mês viraria ~30 linhas de zero.
		List<List<Object>> serie = c.serieTemporal().stream()
				.filter(ponto -> ponto.totalReais().signum() != 0 || ponto.totalComissaoLiquida().signum() != 0)
				.map(ponto -> Arrays.<Object>asList(ponto.data(), ponto.quantidadeOperacoes(), ponto.totalReais(),
						ponto.totalComissaoLiquida()))
				.toList();

		Set<String> moedas = new LinkedHashSet<>(c.volumePorMoeda().keySet());
		moedas.addAll(anterior.volumePorMoeda().keySet());
		List<List<Object>> volumes = moedas.stream()
				.map(moeda -> Arrays.<Object>asList(moeda, c.volumePorMoeda().get(moeda),
						anterior.volumePorMoeda().get(moeda)))
				.toList();

		List<SecaoRelatorio> secoes = List.of(
				new SecaoRelatorio("Resumo", List.of(de("Indicador", TipoColuna.TEXTO), de("Valor", TipoColuna.AUTO)),
						resumo),
				// "dias com movimento" fica fora do título: viraria o nome da aba, cortado em 31 caracteres.
				new SecaoRelatorio("Série temporal", List.of(de("Data", TipoColuna.DATA),
						de("Ordens", TipoColuna.INTEIRO).somada(), de("Total em reais", TipoColuna.DECIMAL).somada(),
						de("Comissão líquida", TipoColuna.DECIMAL).somada()), serie),
				new SecaoRelatorio("Volume por moeda", List.of(de("Moeda", TipoColuna.TEXTO),
						de("Volume — " + rotuloPrincipal.toLowerCase(), TipoColuna.DECIMAL),
						de("Volume — " + rotuloComparado.toLowerCase(), TipoColuna.DECIMAL)), volumes));

		List<Destaque> destaques = List.of(
				new Destaque("Ordens", c.totalOperacoes(), TipoColuna.INTEIRO),
				new Destaque("Total em reais", c.totalReais(), TipoColuna.DECIMAL),
				new Destaque("Comissão líquida", c.totalComissaoLiquida(), TipoColuna.DECIMAL),
				new Destaque("Variação da comissão vs. " + rotuloComparado.toLowerCase(),
						variacao(c.variacaoComissaoLiquidaPercentual()), TipoColuna.TEXTO));

		return new RelatorioTabular("Comparativos e Tendências", "comparativo-" + c.inicio() + "-a-" + c.fim(),
				List.of(rotuloPrincipal + ": " + FormatoBrasileiro.intervalo(c.inicio(), c.fim()),
						rotuloComparado + ": " + FormatoBrasileiro.intervalo(anterior.inicio(), anterior.fim()),
						geradoEm()),
				destaques, secoes);
	}

	private static String descreverDia(PontoSerieTemporal dia) {
		if (dia == null) {
			return null;
		}
		return FormatoBrasileiro.data(dia.data()) + " — comissão " + FormatoBrasileiro.dinheiro(dia.totalComissaoLiquida());
	}

	/** Já vem em pontos percentuais do serviço (12,34 = 12,34%), não como razão. */
	private static String variacao(BigDecimal valor) {
		if (valor == null) {
			return null;
		}
		String sinal = valor.signum() > 0 ? "+" : "";
		return sinal + FormatoBrasileiro.dinheiro(valor) + "%";
	}

	// ------------------------------------------------------------------ Rankings

	RelatorioTabular rankings(RankingsResponse r) {
		return new RelatorioTabular("Rankings por Dimensão", "rankings-" + r.inicio() + "-a-" + r.fim(),
				List.of("Período: " + FormatoBrasileiro.intervalo(r.inicio(), r.fim()), geradoEm()),
				secoesRanking(r, ""));
	}

	/** Modo "dois períodos" da tela: A e B no mesmo arquivo, cada um com suas abas. */
	RelatorioTabular rankingsEntreDoisPeriodos(RankingsResponse a, RankingsResponse b) {
		List<SecaoRelatorio> secoes = new ArrayList<>(secoesRanking(a, "A - "));
		secoes.addAll(secoesRanking(b, "B - "));
		return new RelatorioTabular("Rankings por Dimensão", "rankings-comparacao-" + a.inicio() + "-a-" + b.fim(),
				List.of("Período A: " + FormatoBrasileiro.intervalo(a.inicio(), a.fim()),
						"Período B: " + FormatoBrasileiro.intervalo(b.inicio(), b.fim()), geradoEm()),
				secoes);
	}

	private static List<SecaoRelatorio> secoesRanking(RankingsResponse r, String prefixo) {
		return List.of(
				ranking(prefixo + "Por cliente", "Cliente", r.porCliente()),
				ranking(prefixo + "Por banco", "Banco", r.porBanco()),
				ranking(prefixo + "Por moeda", "Moeda", r.porMoeda()),
				ranking(prefixo + "Por tipo (C-V)", "C/V", r.porTipo()),
				ranking(prefixo + "Por usuário que criou", "Usuário", r.porUsuarioCriador()),
				ranking(prefixo + "Por usuário que completou", "Usuário", r.porUsuarioCompletador()));
	}

	private static SecaoRelatorio ranking(String titulo, String rotulo, List<QuebraItem> itens) {
		List<List<Object>> linhas = new ArrayList<>();
		for (int i = 0; i < itens.size(); i++) {
			QuebraItem item = itens.get(i);
			linhas.add(Arrays.asList(i + 1, item.rotulo(), item.quantidade(), item.totalReais(),
					item.totalComissaoLiquida()));
		}
		return new SecaoRelatorio(titulo, List.of(de("Posição", TipoColuna.INTEIRO), de(rotulo, TipoColuna.TEXTO),
				de("Ordens", TipoColuna.INTEIRO).somada(), de("Total em reais", TipoColuna.DECIMAL).somada(),
				de("Comissão líquida", TipoColuna.DECIMAL).somada()), linhas);
	}

	// ------------------------------------------------------------------ Posição em aberto

	RelatorioTabular posicaoAberto(PosicaoAbertoResponse p, LocalDate hoje) {
		List<List<Object>> ordens = p.operacoes().stream()
				.map(op -> Arrays.<Object>asList(op.idTrade(), op.data(), op.diasEmAberto(), op.clienteNome(),
						op.bancoNome(), op.moeda(), op.valorMe()))
				.toList();
		List<List<Object>> porMoeda = p.exposicaoPorMoeda().entrySet().stream()
				.map(e -> Arrays.<Object>asList(e.getKey(), e.getValue()))
				.toList();

		List<SecaoRelatorio> secoes = List.of(
				new SecaoRelatorio("Ordens em aberto", List.of(de("Código da Ordem", TipoColuna.TEXTO),
						de("Data do Fechamento", TipoColuna.DATA), de("Dias em aberto", TipoColuna.INTEIRO),
						de("Cliente", TipoColuna.TEXTO), de("Banco", TipoColuna.TEXTO), de("Moeda", TipoColuna.TEXTO),
						de("Valor em moeda", TipoColuna.DECIMAL)), ordens),
				new SecaoRelatorio("Exposição por moeda", List.of(de("Moeda", TipoColuna.TEXTO),
						de("Valor em moeda", TipoColuna.DECIMAL)), porMoeda),
				exposicao("Exposição por cliente", "Cliente", p.exposicaoPorCliente()),
				exposicao("Exposição por banco", "Banco", p.exposicaoPorBanco()));

		// Um cartão por moeda (até 3): somar USD com EUR num número só não teria significado
		// — pelo mesmo motivo, as colunas de valor em moeda não têm linha de total.
		List<Destaque> destaques = new ArrayList<>();
		destaques.add(new Destaque("Ordens em andamento", p.totalOperacoesEmAndamento(), TipoColuna.INTEIRO));
		p.exposicaoPorMoeda().entrySet().stream().limit(3).forEach(e -> destaques
				.add(new Destaque("Exposição em " + e.getKey(), e.getValue(), TipoColuna.DECIMAL)));

		return new RelatorioTabular("Posição em Aberto", "posicao-em-aberto-" + hoje,
				List.of("Situação em " + FormatoBrasileiro.data(hoje), geradoEm()), destaques, secoes);
	}

	private static SecaoRelatorio exposicao(String titulo, String rotulo, List<ExposicaoItem> itens) {
		List<List<Object>> linhas = itens.stream()
				.map(item -> Arrays.<Object>asList(item.rotulo(), item.moeda(), item.quantidade(), item.valorMe()))
				.toList();
		return new SecaoRelatorio(titulo, List.of(de(rotulo, TipoColuna.TEXTO), de("Moeda", TipoColuna.TEXTO),
				de("Ordens", TipoColuna.INTEIRO).somada(), de("Valor em moeda", TipoColuna.DECIMAL)), linhas);
	}

	// ------------------------------------------------------------------ Fechamento Diário

	private static final Map<String, String> ROTULO_CV = Map.of("C", "C (Compra)", "V", "V (Venda)");

	/**
	 * Incremento 83: o Fechamento (download e anexo do e-mail agendado) passa a usar o mesmo
	 * exportador e o mesmo visual dos relatórios. Conteúdo e rótulos são os do exportador
	 * anterior ("Total R$", "Comissão Líquida (R$)", status e C/V por extenso), numa aba só —
	 * é um resumo de um dia, lido de uma vez.
	 */
	RelatorioTabular fechamento(FechamentoResponse f) {
		ResultadoFinanceiro r = f.resultadoFinanceiro();

		List<List<Object>> porStatus = new java.util.TreeMap<>(f.resumoOperacional().porStatus()).entrySet().stream()
				.map(e -> Arrays.<Object>asList(rotuloStatus(e.getKey()), e.getValue()))
				.toList();
		List<List<Object>> financeiro = List.of(
				linha("Total R$", r.totalReais()),
				linha("Total Bruto do Câmbio", r.totalBrutoCambio()),
				linha("Comissão Líquida", r.totalComissaoLiquida()),
				linha("Ticket médio", r.ticketMedioReais()),
				linha("Maior operação", r.maiorOperacaoReais()),
				linha("Menor operação", r.menorOperacaoReais()));
		List<List<Object>> volumes = r.volumePorMoeda().entrySet().stream()
				.map(e -> Arrays.<Object>asList(e.getKey(), r.quantidadePorMoeda().getOrDefault(e.getKey(), 0),
						e.getValue()))
				.toList();
		List<List<Object>> exposicao = f.posicaoEmAberto().exposicaoPorMoeda().entrySet().stream()
				.map(e -> Arrays.<Object>asList(e.getKey(), e.getValue()))
				.toList();

		List<SecaoRelatorio> secoes = List.of(
				new SecaoRelatorio("Resumo operacional", List.of(de("Status", TipoColuna.STATUS),
						de("Quantidade", TipoColuna.INTEIRO).somada()), porStatus),
				new SecaoRelatorio("Resultado financeiro (só operações confirmadas)", List.of(de("Métrica", TipoColuna.TEXTO),
						de("Valor (R$)", TipoColuna.DECIMAL)), financeiro),
				// Sem total de volume: cada linha é uma moeda diferente.
				new SecaoRelatorio("Volume por moeda (ME)", List.of(de("Moeda", TipoColuna.TEXTO),
						de("Quantidade", TipoColuna.INTEIRO).somada(), de("Volume (ME)", TipoColuna.DECIMAL)), volumes),
				quebra("Quebra por banco", "Banco", f.quebras().porBanco(), Map.of()),
				quebra("Quebra por cliente", "Cliente", f.quebras().porCliente(), Map.of()),
				quebra("Quebra por moeda", "Moeda", f.quebras().porMoeda(), Map.of()),
				quebra("Quebra por tipo (C/V)", "Tipo", f.quebras().porTipo(), ROTULO_CV),
				new SecaoRelatorio("Posição em aberto (acumulada, todas as datas) · "
						+ f.posicaoEmAberto().totalOperacoesEmAndamento() + " em andamento",
						List.of(de("Moeda", TipoColuna.TEXTO), de("Exposição (ME)", TipoColuna.DECIMAL)), exposicao));

		List<Destaque> destaques = List.of(
				new Destaque("Ordens no dia", f.resumoOperacional().totalOperacoes(), TipoColuna.INTEIRO),
				new Destaque("Confirmadas", r.quantidadeOperacoes(), TipoColuna.INTEIRO),
				new Destaque("Total R$", r.totalReais(), TipoColuna.DECIMAL),
				new Destaque("Comissão Líquida (R$)", r.totalComissaoLiquida(), TipoColuna.DECIMAL),
				new Destaque("Ticket médio", r.ticketMedioReais(), TipoColuna.DECIMAL));

		return new RelatorioTabular("Fechamento Diário — " + FormatoBrasileiro.data(f.data()), "fechamento-" + f.data(),
				List.of("Data: " + FormatoBrasileiro.data(f.data()), geradoEm()), destaques, secoes, true);
	}

	private static SecaoRelatorio quebra(String titulo, String rotulo, List<QuebraItem> itens,
			Map<String, String> rotulos) {
		List<List<Object>> linhas = itens.stream()
				.map(item -> Arrays.<Object>asList(rotulos.getOrDefault(item.rotulo(), item.rotulo()), item.quantidade(),
						item.totalReais(), item.totalComissaoLiquida()))
				.toList();
		return new SecaoRelatorio(titulo, List.of(de(rotulo, TipoColuna.TEXTO), de("Quantidade", TipoColuna.INTEIRO).somada(),
				de("Total R$", TipoColuna.DECIMAL).somada(), de("Comissão Líquida (R$)", TipoColuna.DECIMAL).somada()),
				linhas);
	}

	private static String rotuloStatus(String codigo) {
		try {
			return ROTULO_STATUS.get(StatusOperacao.valueOf(codigo));
		} catch (IllegalArgumentException e) {
			return codigo;
		}
	}

	// ------------------------------------------------------------------ comum

	private static List<Object> linha(String indicador, Object valor) {
		return Arrays.asList(indicador, valor);
	}

	private static String geradoEm() {
		return "Gerado em " + FormatoBrasileiro.dataHora(Instant.now()) + " (horário de Brasília)";
	}

}
