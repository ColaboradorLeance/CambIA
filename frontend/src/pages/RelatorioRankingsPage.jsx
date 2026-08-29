import { useEffect, useState } from "react";
import { api } from "../api/client";
import LoadingState from "../components/LoadingState";
import SeletorPeriodo from "../components/SeletorPeriodo";
import { formatarData } from "../utils/data";
import { PRESETS_PERIODO } from "../utils/periodos";

function formatarMoeda(valor) {
	if (valor === null || valor === undefined) return "—";
	return Number(valor).toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function TabelaRanking({ titulo, itens }) {
	return (
		<div className="fechamento-bloco">
			<h3>{titulo}</h3>
			{itens.length === 0 ? (
				<p className="fechamento-vazio">Nenhum dado nesse período.</p>
			) : (
				<table>
					<thead>
						<tr>
							<th></th>
							<th>Qtd.</th>
							<th>Total R$</th>
							<th>Comissão Líquida</th>
						</tr>
					</thead>
					<tbody>
						{itens.map((item) => (
							<tr key={item.rotulo}>
								<td>{item.rotulo}</td>
								<td>{item.quantidade}</td>
								<td>{formatarMoeda(item.totalReais)}</td>
								<td>{formatarMoeda(item.totalComissaoLiquida)}</td>
							</tr>
						))}
					</tbody>
				</table>
			)}
		</div>
	);
}

function BlocoRankings({ titulo, rankings }) {
	return (
		<div className="comparativo-bloco-rankings">
			<h2>{titulo}</h2>
			<p className="fechamento-periodo-legenda">
				Período: {formatarData(rankings.inicio)} a {formatarData(rankings.fim)} — ordenado por comissão líquida
				(maior primeiro)
			</p>
			<TabelaRanking titulo="Por cliente" itens={rankings.porCliente} />
			<TabelaRanking titulo="Por banco" itens={rankings.porBanco} />
			<TabelaRanking titulo="Por moeda" itens={rankings.porMoeda} />
			<TabelaRanking titulo="Por tipo (C/V)" itens={rankings.porTipo} />
			<TabelaRanking titulo="Por usuário — criou a ordem" itens={rankings.porUsuarioCriador} />
			<TabelaRanking titulo="Por usuário — completou a ordem" itens={rankings.porUsuarioCompletador} />
		</div>
	);
}

export default function RelatorioRankingsPage() {
	const [modo, setModo] = useState("RAPIDO");
	const [periodo, setPeriodo] = useState("MES");

	const [presetA, setPresetA] = useState("MES_PASSADO");
	const [inicioA, setInicioA] = useState("");
	const [fimA, setFimA] = useState("");
	const [presetB, setPresetB] = useState("DOIS_MESES_ATRAS");
	const [inicioB, setInicioB] = useState("");
	const [fimB, setFimB] = useState("");

	const [rankings, setRankings] = useState(null);
	const [rankingsA, setRankingsA] = useState(null);
	const [rankingsB, setRankingsB] = useState(null);
	const [carregando, setCarregando] = useState(true);

	useEffect(() => {
		if (presetA === "PERSONALIZADO") return;
		const intervalo = PRESETS_PERIODO.find((p) => p.valor === presetA).calcular();
		setInicioA(intervalo.inicio);
		setFimA(intervalo.fim);
	}, [presetA]);

	useEffect(() => {
		if (presetB === "PERSONALIZADO") return;
		const intervalo = PRESETS_PERIODO.find((p) => p.valor === presetB).calcular();
		setInicioB(intervalo.inicio);
		setFimB(intervalo.fim);
	}, [presetB]);

	const intervaloAIncompleto = !inicioA || !fimA;
	const intervaloBIncompleto = !inicioB || !fimB;
	const algumIntervaloInvalido = (inicioA && fimA && fimA < inicioA) || (inicioB && fimB && fimB < inicioB);
	const doisPeriodosIndisponivel = modo === "DOIS_PERIODOS"
		&& (intervaloAIncompleto || intervaloBIncompleto || algumIntervaloInvalido);

	async function carregar() {
		if (modo === "DOIS_PERIODOS" && doisPeriodosIndisponivel) {
			setCarregando(false);
			return;
		}
		setCarregando(true);
		try {
			if (modo === "DOIS_PERIODOS") {
				const [a, b] = await Promise.all([
					api.get(`/relatorios/rankings?inicio=${inicioA}&fim=${fimA}`),
					api.get(`/relatorios/rankings?inicio=${inicioB}&fim=${fimB}`),
				]);
				setRankingsA(a);
				setRankingsB(b);
			} else {
				setRankings(await api.get(`/relatorios/rankings?periodo=${periodo}`));
			}
		} catch {
			setRankings(null);
			setRankingsA(null);
			setRankingsB(null);
		} finally {
			setCarregando(false);
		}
	}

	useEffect(() => {
		carregar();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [modo, periodo, inicioA, fimA, inicioB, fimB]);

	return (
		<div>
			<h1>Rankings por Dimensão</h1>

			<div className="relatorio-filtros">
				<label>
					Modo
					<select value={modo} onChange={(e) => setModo(e.target.value)}>
						<option value="RAPIDO">Um período</option>
						<option value="DOIS_PERIODOS">Comparar dois períodos à escolha</option>
					</select>
				</label>
				{modo === "RAPIDO" && (
					<label>
						Período
						<select value={periodo} onChange={(e) => setPeriodo(e.target.value)}>
							<option value="HOJE">Hoje</option>
							<option value="SEMANA">Esta semana</option>
							<option value="MES">Este mês</option>
							<option value="ANO">Este ano</option>
						</select>
					</label>
				)}
			</div>

			{modo === "DOIS_PERIODOS" && (
				<div className="comparativo-dois-periodos">
					<SeletorPeriodo
						titulo="Período A"
						preset={presetA}
						setPreset={setPresetA}
						inicio={inicioA}
						setInicio={setInicioA}
						fim={fimA}
						setFim={setFimA}
					/>
					<SeletorPeriodo
						titulo="Período B"
						preset={presetB}
						setPreset={setPresetB}
						inicio={inicioB}
						setInicio={setInicioB}
						fim={fimB}
						setFim={setFimB}
					/>
				</div>
			)}

			{algumIntervaloInvalido && (
				<p className="fechamento-vazio">A data fim não pode ser anterior à data início, em nenhum dos períodos.</p>
			)}
			{!algumIntervaloInvalido && modo === "DOIS_PERIODOS" && (intervaloAIncompleto || intervaloBIncompleto) && (
				<p className="fechamento-vazio">Escolha as datas dos dois períodos para comparar.</p>
			)}

			{carregando && !doisPeriodosIndisponivel && <LoadingState label="Carregando rankings…" />}

			{!carregando && modo === "RAPIDO" && rankings && <BlocoRankings titulo="Rankings" rankings={rankings} />}

			{!carregando && modo === "DOIS_PERIODOS" && rankingsA && rankingsB && (
				<>
					<BlocoRankings titulo="Rankings — Período A" rankings={rankingsA} />
					<BlocoRankings titulo="Rankings — Período B" rankings={rankingsB} />
				</>
			)}
		</div>
	);
}
