import { useEffect, useState } from "react";
import { api } from "../api/client";
import StatusBadge from "../components/StatusBadge";
import LoadingState from "../components/LoadingState";

function hoje() {
	return new Date().toISOString().slice(0, 10);
}

function fmt(valor) {
	if (valor === null || valor === undefined) return "—";
	return Number(valor).toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function TabelaQuebra({ titulo, itens }) {
	return (
		<div className="fechamento-bloco">
			<h3>{titulo}</h3>
			{itens.length === 0 ? (
				<p className="fechamento-vazio">Nenhuma ordem nesse dia.</p>
			) : (
				<table>
					<thead>
						<tr>
							<th></th>
							<th>Qtd.</th>
							<th>Total R$</th>
							<th>Comissão R$</th>
						</tr>
					</thead>
					<tbody>
						{itens.map((item) => (
							<tr key={item.rotulo}>
								<td>{item.rotulo}</td>
								<td>{item.quantidade}</td>
								<td>{fmt(item.totalReais)}</td>
								<td>{fmt(item.totalComissaoLiquida)}</td>
							</tr>
						))}
					</tbody>
				</table>
			)}
		</div>
	);
}

export default function FechamentoPage() {
	const [data, setData] = useState(hoje());
	const [fechamento, setFechamento] = useState(null);
	const [carregando, setCarregando] = useState(true);

	async function carregar() {
		setCarregando(true);
		try {
			setFechamento(await api.get(`/fechamentos/${data}`));
		} catch {
			setFechamento(null);
		} finally {
			setCarregando(false);
		}
	}

	useEffect(() => {
		carregar();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [data]);

	async function baixar(formato) {
		try {
			await api.baixarArquivo(`/fechamentos/${data}/${formato}`, `fechamento-${data}.${formato}`);
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	return (
		<div>
			<h1>Fechamento Diário</h1>

			<div className="fechamento-topo">
				<label className="fechamento-data">
					Data
					<input type="date" value={data} onChange={(e) => setData(e.target.value)} />
				</label>
				<div className="fechamento-downloads">
					<button type="button" className="btn btn-secondary" onClick={() => baixar("pdf")}>
						Baixar PDF
					</button>
					<button type="button" className="btn btn-secondary" onClick={() => baixar("xlsx")}>
						Baixar Excel
					</button>
				</div>
			</div>

			{carregando && <LoadingState label="Carregando fechamento…" />}

			{!carregando && fechamento && (
				<>
					<div className="fechamento-bloco">
						<h3>Resumo operacional</h3>
						<p>
							Total de ordens no dia: <strong>{fechamento.resumoOperacional.totalOperacoes}</strong>
							{Object.entries(fechamento.resumoOperacional.porStatus).map(([status, qtd]) => (
								<span key={status} className="fechamento-tag">
									{status}: {qtd}
								</span>
							))}
						</p>
					</div>

					<div className="fechamento-bloco fechamento-destaque">
						<h3>Resultado financeiro (só ordens confirmadas)</h3>
						<div className="fechamento-grid">
							<div>
								<span className="fechamento-label">Quantidade de operações</span>
								<span className="fechamento-valor">{fechamento.resultadoFinanceiro.quantidadeOperacoes}</span>
							</div>
							<div>
								<span className="fechamento-label">Total R$</span>
								<span className="fechamento-valor">{fmt(fechamento.resultadoFinanceiro.totalReais)}</span>
							</div>
							<div>
								<span className="fechamento-label">Total Bruto do Câmbio</span>
								<span className="fechamento-valor">{fmt(fechamento.resultadoFinanceiro.totalBrutoCambio)}</span>
							</div>
							<div>
								<span className="fechamento-label">Comissão Líquida (lucro do dia)</span>
								<span className="fechamento-valor fechamento-lucro">
									{fmt(fechamento.resultadoFinanceiro.totalComissaoLiquida)}
								</span>
							</div>
							<div>
								<span className="fechamento-label">Ticket médio</span>
								<span className="fechamento-valor">{fmt(fechamento.resultadoFinanceiro.ticketMedioReais)}</span>
							</div>
							<div>
								<span className="fechamento-label">Maior ordem</span>
								<span className="fechamento-valor">{fmt(fechamento.resultadoFinanceiro.maiorOperacaoReais)}</span>
							</div>
							<div>
								<span className="fechamento-label">Menor ordem</span>
								<span className="fechamento-valor">{fmt(fechamento.resultadoFinanceiro.menorOperacaoReais)}</span>
							</div>
						</div>
						{Object.keys(fechamento.resultadoFinanceiro.volumePorMoeda).length > 0 && (
							<table>
								<thead>
									<tr>
										<th>Moeda</th>
										<th>Quantidade</th>
										<th>Volume (ME)</th>
									</tr>
								</thead>
								<tbody>
									{Object.entries(fechamento.resultadoFinanceiro.volumePorMoeda).map(([moeda, volume]) => (
										<tr key={moeda}>
											<td>{moeda}</td>
											<td>{fechamento.resultadoFinanceiro.quantidadePorMoeda[moeda] || 0}</td>
											<td>{fmt(volume)}</td>
										</tr>
									))}
								</tbody>
							</table>
						)}
					</div>

					<TabelaQuebra titulo="Por banco" itens={fechamento.quebras.porBanco} />
					<TabelaQuebra titulo="Por cliente" itens={fechamento.quebras.porCliente} />
					<TabelaQuebra titulo="Por moeda" itens={fechamento.quebras.porMoeda} />
					<TabelaQuebra titulo="Por tipo (C/V)" itens={fechamento.quebras.porTipo} />

					<div className="fechamento-bloco">
						<h3>Posição em aberto (acumulada, não só deste dia)</h3>
						<p>
							Ordens "Em andamento": <strong>{fechamento.posicaoEmAberto.totalOperacoesEmAndamento}</strong>
						</p>
						{Object.keys(fechamento.posicaoEmAberto.exposicaoPorMoeda).length > 0 && (
							<table>
								<thead>
									<tr>
										<th>Moeda</th>
										<th>Exposição (ME)</th>
									</tr>
								</thead>
								<tbody>
									{Object.entries(fechamento.posicaoEmAberto.exposicaoPorMoeda).map(([moeda, valor]) => (
										<tr key={moeda}>
											<td>{moeda}</td>
											<td>{fmt(valor)}</td>
										</tr>
									))}
								</tbody>
							</table>
						)}
					</div>

					<div className="fechamento-bloco">
						<h3>Ordens do dia</h3>
						<table>
							<thead>
								<tr>
									<th>ID do trade</th>
									<th>C/V</th>
									<th>Moeda</th>
									<th>Valor ME</th>
									<th>Status</th>
									<th>R$</th>
									<th>Comissão</th>
								</tr>
							</thead>
							<tbody>
								{fechamento.resumoOperacional.operacoes.map((op) => (
									<tr key={op.id}>
										<td>{op.idTrade}</td>
										<td>{op.cv}</td>
										<td>{op.moeda}</td>
										<td className="mono">{fmt(op.valorMe)}</td>
										<td>
											<StatusBadge status={op.status} />
										</td>
										<td className="mono">{fmt(op.reais)}</td>
										<td className="mono">{fmt(op.comissaoLiquida)}</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				</>
			)}
		</div>
	);
}
