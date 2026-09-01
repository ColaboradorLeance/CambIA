import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import StatusBadge from "../components/StatusBadge";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";
import {
	IconList,
	IconMoney,
	IconClock,
	IconVolume,
	IconTrendUp,
	IconPlus,
	IconCalendarCheck,
	IconTrend,
} from "../components/icons";

function formatarMoeda(valor) {
	if (valor === null || valor === undefined) return "—";
	return Number(valor).toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatarPercentual(valor) {
	if (valor === null || valor === undefined) return null;
	const sinal = Number(valor) > 0 ? "+" : "";
	return `${sinal}${Number(valor).toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`;
}

function dataDeHoje() {
	const texto = new Date().toLocaleDateString("pt-BR", {
		weekday: "long",
		day: "numeric",
		month: "long",
		year: "numeric",
	});
	return texto.charAt(0).toUpperCase() + texto.slice(1);
}

export default function PainelPage() {
	const { usuario } = useAuth();
	const [carregando, setCarregando] = useState(true);
	const [operacoesHoje, setOperacoesHoje] = useState(null);
	const [comparativoMes, setComparativoMes] = useState(null);
	const [posicaoAberto, setPosicaoAberto] = useState(null);
	const [ultimasOperacoes, setUltimasOperacoes] = useState([]);

	useEffect(() => {
		async function carregar() {
			setCarregando(true);
			try {
				const [hoje, comparativo, aberto, todas] = await Promise.all([
					api.get("/relatorios/operacoes?periodo=HOJE"),
					api.get("/relatorios/comparativo?periodo=MES"),
					api.get("/relatorios/posicao-aberto"),
					api.get("/operacoes"),
				]);
				setOperacoesHoje(hoje);
				setComparativoMes(comparativo);
				setPosicaoAberto(aberto);
				setUltimasOperacoes([...todas].sort((a, b) => b.id - a.id).slice(0, 5));
			} catch {
				// erro já mostrado como pop-up pelo api/client.js
			} finally {
				setCarregando(false);
			}
		}
		carregar();
	}, []);

	if (carregando) {
		return (
			<div>
				<h1>Painel</h1>
				<LoadingState label="Carregando resumo…" />
			</div>
		);
	}

	if (!operacoesHoje || !comparativoMes || !posicaoAberto) {
		return (
			<div>
				<h1>Painel</h1>
				<EmptyState
					title="Não foi possível carregar o painel"
					message="Veja o pop-up de erro no canto da tela para o motivo, ou tente novamente."
				/>
			</div>
		);
	}

	const confirmadas = operacoesHoje.filter((op) => op.status === "CONFIRMADO").length;
	const emAndamento = operacoesHoje.filter((op) => op.status === "ANDAMENTO").length;
	const canceladas = operacoesHoje.filter((op) => op.status === "CANCELADO").length;
	const maisAntiga = posicaoAberto.operacoes[0];
	const variacao = formatarPercentual(comparativoMes.variacaoComissaoLiquidaPercentual);

	return (
		<div>
			<div className="painel-header">
				<div>
					<h1>Olá, {usuario?.nome}</h1>
					<p>{dataDeHoje()} · resumo de hoje</p>
				</div>
				<Link to="/ordens" className="btn btn-primary">
					<IconPlus size={15} strokeWidth="2.4" />
					Nova ordem
				</Link>
			</div>

			<div className="kpi-grid">
				<div className="kpi-card">
					<div className="kpi-card-head">
						<span className="stat-label">Ordens hoje</span>
						<div className="kpi-icon kpi-icon-accent">
							<IconList size={16} />
						</div>
					</div>
					<div className="stat-value">{operacoesHoje.length}</div>
					<div className="kpi-caption">
						{operacoesHoje.length === 0
							? "nenhuma ordem ainda"
							: `${confirmadas} confirmadas · ${emAndamento} em andamento${
									canceladas > 0 ? ` · ${canceladas} canceladas` : ""
								}`}
					</div>
				</div>

				<div className="kpi-card">
					<div className="kpi-card-head">
						<span className="stat-label">Comissão líquida (mês)</span>
						<div className="kpi-icon kpi-icon-success">
							<IconMoney size={16} />
						</div>
					</div>
					<div className="stat-value fechamento-lucro">{formatarMoeda(comparativoMes.totalComissaoLiquida)}</div>
					{variacao && (
						<div className="kpi-caption kpi-caption-positive">
							<IconTrendUp size={12} strokeWidth="2.5" />
							{variacao} vs. mês anterior
						</div>
					)}
				</div>

				<div className="kpi-card">
					<div className="kpi-card-head">
						<span className="stat-label">Posições em aberto</span>
						<div className="kpi-icon kpi-icon-warning">
							<IconClock size={16} />
						</div>
					</div>
					<div className="stat-value">{posicaoAberto.totalOperacoesEmAndamento}</div>
					<div className="kpi-caption">
						{maisAntiga ? `mais antiga: ${maisAntiga.diasEmAberto} dias` : "nenhuma em aberto"}
					</div>
				</div>

				<div className="kpi-card">
					<div className="kpi-card-head">
						<span className="stat-label">Total R$ (mês)</span>
						<div className="kpi-icon kpi-icon-neutral">
							<IconVolume size={16} />
						</div>
					</div>
					<div className="stat-value">{formatarMoeda(comparativoMes.totalReais)}</div>
					<div className="kpi-caption">ordens confirmadas do mês</div>
				</div>
			</div>

			<div className="painel-lower">
				<div className="card">
					<h3>Ações rápidas</h3>
					<div className="quick-actions">
						<Link to="/ordens" className="quick-action-link">
							<IconPlus size={15} strokeWidth="2.2" />
							Registrar ordem
						</Link>
						<Link to="/fechamento" className="quick-action-link">
							<IconCalendarCheck size={15} strokeWidth="2.2" />
							Ver fechamento de hoje
						</Link>
						<Link to="/relatorio/ordens" className="quick-action-link">
							<IconTrend size={15} strokeWidth="2.2" />
							Abrir relatórios
						</Link>
					</div>
				</div>

				<div className="card">
					<div className="card-head">
						<h3 style={{ margin: 0 }}>Últimas ordens</h3>
						<Link to="/ordens" style={{ fontSize: "12.5px", fontWeight: 600 }}>
							Ver todas
						</Link>
					</div>
					{ultimasOperacoes.length === 0 ? (
						<p className="fechamento-vazio">Nenhuma ordem registrada ainda.</p>
					) : (
						<table>
							<thead>
								<tr>
									<th>Trade</th>
									<th>Cliente</th>
									<th>Status</th>
									<th style={{ textAlign: "right" }}>R$</th>
								</tr>
							</thead>
							<tbody>
								{ultimasOperacoes.map((op) => (
									<tr key={op.id}>
										<td className="mono">{op.idTrade}</td>
										<td>{op.clienteNome || `#${op.clienteId}`}</td>
										<td>
											<StatusBadge status={op.status} />
										</td>
										<td className="mono" style={{ textAlign: "right" }}>
											{formatarMoeda(op.reais)}
										</td>
									</tr>
								))}
							</tbody>
						</table>
					)}
				</div>
			</div>
		</div>
	);
}
