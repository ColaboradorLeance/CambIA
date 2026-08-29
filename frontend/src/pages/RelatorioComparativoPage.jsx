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

function formatarPercentual(valor) {
	if (valor === null || valor === undefined) return "—";
	const sinal = Number(valor) > 0 ? "+" : "";
	return `${sinal}${Number(valor).toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`;
}

function TabelaVolumePorMoeda({ volumePorMoeda }) {
	const moedas = Object.keys(volumePorMoeda || {});
	if (moedas.length === 0) {
		return <p className="fechamento-vazio">Nenhuma moeda com ordem completa nesse período.</p>;
	}
	return (
		<table>
			<thead>
				<tr>
					<th>Moeda</th>
					<th>Volume (ME)</th>
				</tr>
			</thead>
			<tbody>
				{moedas.map((moeda) => (
					<tr key={moeda}>
						<td>{moeda}</td>
						<td>{formatarMoeda(volumePorMoeda[moeda])}</td>
					</tr>
				))}
			</tbody>
		</table>
	);
}

export default function RelatorioComparativoPage() {
	const [modo, setModo] = useState("RAPIDO");
	const [periodo, setPeriodo] = useState("MES");

	const [presetA, setPresetA] = useState("MES_PASSADO");
	const [inicioA, setInicioA] = useState("");
	const [fimA, setFimA] = useState("");
	const [presetB, setPresetB] = useState("DOIS_MESES_ATRAS");
	const [inicioB, setInicioB] = useState("");
	const [fimB, setFimB] = useState("");

	const [comparativo, setComparativo] = useState(null);
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
			const query = modo === "DOIS_PERIODOS"
				? `inicioA=${inicioA}&fimA=${fimA}&inicioB=${inicioB}&fimB=${fimB}`
				: `periodo=${periodo}`;
			setComparativo(await api.get(`/relatorios/comparativo?${query}`));
		} catch {
			setComparativo(null);
		} finally {
			setCarregando(false);
		}
	}

	useEffect(() => {
		carregar();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [modo, periodo, inicioA, fimA, inicioB, fimB]);

	const diasComMovimento = comparativo?.serieTemporal.filter(
		(p) => Number(p.totalReais) !== 0 || Number(p.totalComissaoLiquida) !== 0
	);

	const rotuloPrincipal = modo === "DOIS_PERIODOS" ? "Período A" : "Período atual";
	const rotuloComparado = modo === "DOIS_PERIODOS" ? "Período B" : "Período anterior";

	return (
		<div>
			<h1>Comparativos e Tendências</h1>

			<div className="relatorio-filtros">
				<label>
					Modo
					<select value={modo} onChange={(e) => setModo(e.target.value)}>
						<option value="RAPIDO">Período atual x anterior</option>
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

			{carregando && !doisPeriodosIndisponivel && <LoadingState label="Carregando comparativo…" />}

			{!carregando && comparativo && (
				<>
					<div className="fechamento-bloco fechamento-destaque">
						<h3>
							{rotuloPrincipal} ({formatarData(comparativo.inicio)} a {formatarData(comparativo.fim)})
						</h3>
						<div className="fechamento-grid">
							<div>
								<span className="fechamento-label">Quantidade de ordens</span>
								<span className="fechamento-valor">{comparativo.totalOperacoes}</span>
							</div>
							<div>
								<span className="fechamento-label">Total R$ (volume financeiro)</span>
								<span className="fechamento-valor">{formatarMoeda(comparativo.totalReais)}</span>
							</div>
							<div>
								<span className="fechamento-label">Comissão Líquida</span>
								<span className="fechamento-valor fechamento-lucro">
									{formatarMoeda(comparativo.totalComissaoLiquida)}
								</span>
							</div>
							<div>
								<span className="fechamento-label">Média diária de comissão</span>
								<span className="fechamento-valor">
									{formatarMoeda(comparativo.mediaDiariaComissaoLiquida)}
								</span>
							</div>
							<div>
								<span className="fechamento-label">Melhor dia</span>
								<span className="fechamento-valor">
									{comparativo.melhorDia
										? `${formatarData(comparativo.melhorDia.data)} (${formatarMoeda(comparativo.melhorDia.totalComissaoLiquida)})`
										: "—"}
								</span>
							</div>
							<div>
								<span className="fechamento-label">Pior dia</span>
								<span className="fechamento-valor">
									{comparativo.piorDia
										? `${formatarData(comparativo.piorDia.data)} (${formatarMoeda(comparativo.piorDia.totalComissaoLiquida)})`
										: "—"}
								</span>
							</div>
						</div>
						<h4 className="comparativo-subtitulo-volume">Volume por moeda</h4>
						<TabelaVolumePorMoeda volumePorMoeda={comparativo.volumePorMoeda} />
					</div>

					<div className="fechamento-bloco">
						<h3>
							{rotuloComparado} ({formatarData(comparativo.periodoAnterior.inicio)} a{" "}
							{formatarData(comparativo.periodoAnterior.fim)})
						</h3>
						<div className="fechamento-grid">
							<div>
								<span className="fechamento-label">Quantidade de ordens</span>
								<span className="fechamento-valor">{comparativo.periodoAnterior.totalOperacoes}</span>
							</div>
							<div>
								<span className="fechamento-label">Total R$ (volume financeiro)</span>
								<span className="fechamento-valor">{formatarMoeda(comparativo.periodoAnterior.totalReais)}</span>
							</div>
							<div>
								<span className="fechamento-label">Comissão do período</span>
								<span className="fechamento-valor">
									{formatarMoeda(comparativo.periodoAnterior.totalComissaoLiquida)}
								</span>
							</div>
							<div>
								<span className="fechamento-label">Variação ({rotuloPrincipal} vs {rotuloComparado})</span>
								<span className="fechamento-valor">
									{formatarPercentual(comparativo.variacaoComissaoLiquidaPercentual)}
								</span>
							</div>
						</div>
						<h4 className="comparativo-subtitulo-volume">Volume por moeda</h4>
						<TabelaVolumePorMoeda volumePorMoeda={comparativo.periodoAnterior.volumePorMoeda} />
					</div>

					<div className="fechamento-bloco">
						<h3>Série temporal de {rotuloPrincipal.toLowerCase()} (dias com movimento)</h3>
						{diasComMovimento.length === 0 ? (
							<p className="fechamento-vazio">Nenhum dia com movimento nesse período.</p>
						) : (
							<table>
								<thead>
									<tr>
										<th>Data</th>
										<th>Total R$</th>
										<th>Comissão Líquida</th>
									</tr>
								</thead>
								<tbody>
									{diasComMovimento.map((ponto) => (
										<tr key={ponto.data}>
											<td>{formatarData(ponto.data)}</td>
											<td>{formatarMoeda(ponto.totalReais)}</td>
											<td>{formatarMoeda(ponto.totalComissaoLiquida)}</td>
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
