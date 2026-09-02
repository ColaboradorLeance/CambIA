import { useEffect, useState } from "react";
import { api } from "../api/client";
import StatusBadge from "../components/StatusBadge";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";
import { formatarData } from "../utils/data";
import { formatarMoeda, calcularFundo } from "../utils/formatacao";

const FILTROS_VAZIOS = {
	periodo: "HOJE",
	clienteId: "",
	bancoId: "",
	moeda: "",
	cv: "",
	status: "",
};

function paraQueryString(filtros) {
	const params = new URLSearchParams();
	Object.entries(filtros).forEach(([chave, valor]) => {
		if (valor !== "") params.set(chave, valor);
	});
	return params.toString();
}

export default function RelatorioOperacoesPage() {
	const [filtros, setFiltros] = useState(FILTROS_VAZIOS);
	const [operacoes, setOperacoes] = useState([]);
	const [clientes, setClientes] = useState([]);
	const [bancos, setBancos] = useState([]);
	const [carregando, setCarregando] = useState(true);

	async function carregarListasDeApoio() {
		try {
			const [cli, ban] = await Promise.all([api.get("/clientes"), api.get("/bancos")]);
			setClientes(cli);
			setBancos(ban);
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	async function carregarRelatorio() {
		setCarregando(true);
		try {
			setOperacoes(await api.get(`/relatorios/operacoes?${paraQueryString(filtros)}`));
		} catch {
			setOperacoes([]);
		} finally {
			setCarregando(false);
		}
	}

	useEffect(() => {
		carregarListasDeApoio();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	useEffect(() => {
		carregarRelatorio();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [filtros]);

	function nomeCliente(id) {
		return clientes.find((c) => c.id === id)?.nome || `#${id}`;
	}

	function nomeBanco(id) {
		return bancos.find((b) => b.id === id)?.nome || `#${id}`;
	}

	function atualizarFiltro(campo, valor) {
		setFiltros({ ...filtros, [campo]: valor });
	}

	const totalReais = operacoes.reduce((soma, op) => soma + (op.reais ? Number(op.reais) : 0), 0);
	const totalComissao = operacoes.reduce((soma, op) => soma + (op.comissaoLiquida ? Number(op.comissaoLiquida) : 0), 0);

	return (
		<div>
			<h1>Relatório de Ordens</h1>

			<div className="relatorio-filtros">
				<label>
					Período
					<select value={filtros.periodo} onChange={(e) => atualizarFiltro("periodo", e.target.value)}>
						<option value="HOJE">Hoje</option>
						<option value="SEMANA">Esta semana</option>
						<option value="MES">Este mês</option>
						<option value="ANO">Este ano</option>
					</select>
				</label>
				<label>
					Cliente
					<select value={filtros.clienteId} onChange={(e) => atualizarFiltro("clienteId", e.target.value)}>
						<option value="">Todos</option>
						{clientes.map((c) => (
							<option key={c.id} value={c.id}>
								{c.nome}
							</option>
						))}
					</select>
				</label>
				<label>
					Banco
					<select value={filtros.bancoId} onChange={(e) => atualizarFiltro("bancoId", e.target.value)}>
						<option value="">Todos</option>
						{bancos.map((b) => (
							<option key={b.id} value={b.id}>
								{b.nome}
							</option>
						))}
					</select>
				</label>
				<label>
					Moeda
					<input
						value={filtros.moeda}
						onChange={(e) => atualizarFiltro("moeda", e.target.value)}
						placeholder="Todas"
					/>
				</label>
				<label>
					C/V
					<select value={filtros.cv} onChange={(e) => atualizarFiltro("cv", e.target.value)}>
						<option value="">Todos</option>
						<option value="C">Compra</option>
						<option value="V">Venda</option>
					</select>
				</label>
				<label>
					Status
					<select value={filtros.status} onChange={(e) => atualizarFiltro("status", e.target.value)}>
						<option value="">Todos</option>
						<option value="ANDAMENTO">Em andamento</option>
						<option value="CONFIRMADO">Confirmado</option>
						<option value="CANCELADO">Cancelado</option>
					</select>
				</label>
			</div>

			<div className="fechamento-bloco fechamento-destaque">
				<div className="fechamento-grid">
					<div>
						<span className="fechamento-label">Ordens encontradas</span>
						<span className="fechamento-valor">{operacoes.length}</span>
					</div>
					<div>
						<span className="fechamento-label">Total R$</span>
						<span className="fechamento-valor">{formatarMoeda(totalReais)}</span>
					</div>
					<div>
						<span className="fechamento-label">Comissão Líquida (confirmadas)</span>
						<span className="fechamento-valor fechamento-lucro">{formatarMoeda(totalComissao)}</span>
					</div>
				</div>
			</div>

			{carregando ? (
				<LoadingState label="Carregando ordens…" />
			) : operacoes.length === 0 ? (
				<EmptyState title="Nenhuma ordem encontrada" message="Ajuste os filtros acima e tente novamente." />
			) : (
				<div className="table-card">
					<table>
						<thead>
							<tr>
								<th>ID do trade</th>
								<th>Data</th>
								<th>Cliente</th>
								<th>Banco</th>
								<th>C/V</th>
								<th>Tipo</th>
								<th>Fundo</th>
								<th>Moeda</th>
								<th>Valor ME</th>
								<th>Status</th>
								<th>R$</th>
								<th>Valor Absoluto</th>
								<th>Comissão</th>
								<th>Criado por</th>
								<th>Completado por</th>
							</tr>
						</thead>
						<tbody>
							{operacoes.map((op) => (
								<tr key={op.id}>
									<td className="mono">{op.idTrade}</td>
									<td>{formatarData(op.data)}</td>
									<td>{nomeCliente(op.clienteId)}</td>
									<td>{nomeBanco(op.bancoId)}</td>
									<td>{op.cv}</td>
									<td>{op.prCrVir}</td>
									<td>{calcularFundo(op.prCrVir)}</td>
									<td>{op.moeda}</td>
									<td className="mono">{formatarMoeda(op.valorMe)}</td>
									<td>
										<StatusBadge status={op.status} />
									</td>
									<td className="mono">{formatarMoeda(op.reais)}</td>
									<td className="mono">{formatarMoeda(op.valorAbsoluto)}</td>
									<td className="mono">{formatarMoeda(op.comissaoLiquida)}</td>
									<td>{op.criadoPorNome || "—"}</td>
									<td>{op.completadoPorNome || "—"}</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}
		</div>
	);
}
