import { useEffect, useState } from "react";
import { api } from "../api/client";
import LoadingState from "../components/LoadingState";
import { formatarData } from "../utils/data";

function formatarMoeda(valor) {
	if (valor === null || valor === undefined) return "—";
	return Number(valor).toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 6 });
}

function TabelaExposicao({ titulo, itens }) {
	return (
		<div className="fechamento-bloco">
			<h3>{titulo}</h3>
			{itens.length === 0 ? (
				<p className="fechamento-vazio">Nenhuma exposição em aberto.</p>
			) : (
				<table>
					<thead>
						<tr>
							<th></th>
							<th>Moeda</th>
							<th>Qtd.</th>
							<th>Valor ME</th>
						</tr>
					</thead>
					<tbody>
						{itens.map((item) => (
							<tr key={`${item.rotulo}-${item.moeda}`}>
								<td>{item.rotulo}</td>
								<td>{item.moeda}</td>
								<td>{item.quantidade}</td>
								<td>{formatarMoeda(item.valorMe)}</td>
							</tr>
						))}
					</tbody>
				</table>
			)}
		</div>
	);
}

export default function RelatorioPosicaoAbertoPage() {
	const [posicao, setPosicao] = useState(null);
	const [diasMinimo, setDiasMinimo] = useState(0);
	const [carregando, setCarregando] = useState(true);

	async function carregar() {
		setCarregando(true);
		try {
			setPosicao(await api.get("/relatorios/posicao-aberto"));
		} catch {
			setPosicao(null);
		} finally {
			setCarregando(false);
		}
	}

	useEffect(() => {
		carregar();
	}, []);

	const operacoesFiltradas = posicao?.operacoes.filter((op) => op.diasEmAberto >= Number(diasMinimo || 0));

	return (
		<div>
			<h1>Posição em Aberto</h1>

			{carregando && <LoadingState label="Carregando posição em aberto…" />}

			{!carregando && posicao && (
				<>
					<div className="fechamento-bloco fechamento-destaque">
						<div className="fechamento-grid">
							<div>
								<span className="fechamento-label">Ordens "Em andamento"</span>
								<span className="fechamento-valor">{posicao.totalOperacoesEmAndamento}</span>
							</div>
						</div>
						{Object.keys(posicao.exposicaoPorMoeda).length > 0 && (
							<table>
								<thead>
									<tr>
										<th>Moeda</th>
										<th>Exposição (ME)</th>
									</tr>
								</thead>
								<tbody>
									{Object.entries(posicao.exposicaoPorMoeda).map(([moeda, valor]) => (
										<tr key={moeda}>
											<td>{moeda}</td>
											<td>{formatarMoeda(valor)}</td>
										</tr>
									))}
								</tbody>
							</table>
						)}
					</div>

					<TabelaExposicao titulo="Exposição por cliente" itens={posicao.exposicaoPorCliente} />
					<TabelaExposicao titulo="Exposição por banco" itens={posicao.exposicaoPorBanco} />

					<div className="fechamento-bloco">
						<h3>Ordens em aberto (envelhecimento)</h3>
						<label className="relatorio-filtro-inline">
							Mostrar ordens com pelo menos
							<input
								type="number"
								min="0"
								value={diasMinimo}
								onChange={(e) => setDiasMinimo(e.target.value)}
							/>
							dias em aberto
						</label>
						{operacoesFiltradas.length === 0 ? (
							<p className="fechamento-vazio">Nenhuma ordem em aberto nesse filtro.</p>
						) : (
							<table>
								<thead>
									<tr>
										<th>ID do trade</th>
										<th>Data</th>
										<th>Dias em aberto</th>
										<th>Cliente</th>
										<th>Banco</th>
										<th>Moeda</th>
										<th>Valor ME</th>
									</tr>
								</thead>
								<tbody>
									{operacoesFiltradas.map((op) => (
										<tr key={op.id}>
											<td>{op.idTrade}</td>
											<td>{formatarData(op.data)}</td>
											<td>{op.diasEmAberto}</td>
											<td>{op.clienteNome}</td>
											<td>{op.bancoNome}</td>
											<td>{op.moeda}</td>
											<td>{formatarMoeda(op.valorMe)}</td>
										</tr>
									))}
								</tbody>
							</table>
						)}
					</div>
				</>
			)}
		</div>
	);
}
