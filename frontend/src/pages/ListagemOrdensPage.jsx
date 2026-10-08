import { useEffect, useState } from "react";
import { api } from "../api/client";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";
import { formatarData, formatarDataHora } from "../utils/data";
import { formatarMoeda, formatarPercentual, formatarSpreadEmissao, calcularFundo } from "../utils/formatacao";

const FILTROS_VAZIOS = {
	dataFechamento: "",
	moeda: "",
	valorMoeda: "",
	cnpj: "",
	nome: "",
	idTrade: "",
	cv: "",
	tipo: "",
	banco: "",
	fundo: "",
	criadoPor: "",
	completadoPor: "",
};

// Data local (aaaa-mm-dd) do instante de conclusão, no fuso do navegador — pra comparar
// com o valor de um <input type="date">, que também é sempre local.
function dataLocalIso(instanteIso) {
	if (!instanteIso) return null;
	const d = new Date(instanteIso);
	const ano = d.getFullYear();
	const mes = String(d.getMonth() + 1).padStart(2, "0");
	const dia = String(d.getDate()).padStart(2, "0");
	return `${ano}-${mes}-${dia}`;
}

function contemTexto(valor, filtro) {
	if (!filtro) return true;
	return (valor || "").toLowerCase().includes(filtro.toLowerCase());
}

export default function ListagemOrdensPage() {
	const [operacoes, setOperacoes] = useState([]);
	const [filtros, setFiltros] = useState(FILTROS_VAZIOS);
	const [carregando, setCarregando] = useState(true);

	useEffect(() => {
		async function carregar() {
			setCarregando(true);
			try {
				setOperacoes(await api.get("/operacoes"));
			} catch {
				// erro já mostrado como pop-up pelo api/client.js
			} finally {
				setCarregando(false);
			}
		}
		carregar();
	}, []);

	function atualizarFiltro(campo, valor) {
		setFiltros((atual) => ({ ...atual, [campo]: valor }));
	}

	const confirmadas = operacoes.filter((op) => op.status === "CONFIRMADO");

	// Moeda não tem domínio fechado no sistema (suporta qualquer ISO 4217, cadastro
	// futuro — ver docs/dominio.md) — por isso as opções do <select> vêm das moedas que
	// realmente aparecem nas ordens já confirmadas, e não de uma lista fixa.
	const moedasDisponiveis = [...new Set(confirmadas.map((op) => op.moeda).filter(Boolean))].sort();

	// Fundo deixou de ter domínio fechado P/M (Incremento 75) — as opções do filtro vêm
	// das letras que já aparecem nas ordens carregadas, mesmo critério das moedas.
	const fundosDisponiveis = [...new Set(confirmadas.map((op) => op.fundo || calcularFundo(op.prCrVir)).filter(Boolean))].sort();

	const filtradas = confirmadas.filter((op) => {
		if (filtros.dataFechamento && dataLocalIso(op.completadoEm) !== filtros.dataFechamento) return false;
		if (filtros.moeda && op.moeda !== filtros.moeda) return false;
		if (filtros.valorMoeda && !formatarMoeda(op.valorMe).includes(filtros.valorMoeda)) return false;
		if (!contemTexto(op.clienteDocumento, filtros.cnpj)) return false;
		if (!contemTexto(op.clienteNome, filtros.nome)) return false;
		if (!contemTexto(op.idTrade, filtros.idTrade)) return false;
		if (filtros.cv && op.cv !== filtros.cv) return false;
		if (filtros.tipo && (op.prCrVir || "").toLowerCase() !== filtros.tipo.toLowerCase()) return false;
		if (!contemTexto(op.bancoNome, filtros.banco)) return false;
		if (filtros.fundo && (op.fundo || calcularFundo(op.prCrVir)) !== filtros.fundo) return false;
		if (!contemTexto(op.criadoPorNome, filtros.criadoPor)) return false;
		if (!contemTexto(op.completadoPorNome, filtros.completadoPor)) return false;
		return true;
	});

	const algumFiltroAtivo = Object.values(filtros).some((v) => v !== "");

	return (
		<div>
			<h1>Listagem de Ordens</h1>
			<p className="fechamento-periodo-legenda">Ordens já confirmadas, com os valores calculados.</p>

			<div className="relatorio-filtros">
				<label>
					Data do Fechamento
					<input
						type="date"
						value={filtros.dataFechamento}
						onChange={(e) => atualizarFiltro("dataFechamento", e.target.value)}
					/>
				</label>
				<label>
					Moeda
					<select value={filtros.moeda} onChange={(e) => atualizarFiltro("moeda", e.target.value)}>
						<option value="">Todas</option>
						{moedasDisponiveis.map((moeda) => (
							<option key={moeda} value={moeda}>
								{moeda}
							</option>
						))}
					</select>
				</label>
				<label>
					Valor em moeda
					<input
						value={filtros.valorMoeda}
						onChange={(e) => atualizarFiltro("valorMoeda", e.target.value)}
						placeholder="Todos"
					/>
				</label>
				<label>
					CNPJ/CPF
					<input
						value={filtros.cnpj}
						onChange={(e) => atualizarFiltro("cnpj", e.target.value)}
						placeholder="Todos"
					/>
				</label>
				<label>
					Nome
					<input
						value={filtros.nome}
						onChange={(e) => atualizarFiltro("nome", e.target.value)}
						placeholder="Todos"
					/>
				</label>
				<label>
					Código da Ordem
					<input
						value={filtros.idTrade}
						onChange={(e) => atualizarFiltro("idTrade", e.target.value)}
						placeholder="Todos"
					/>
				</label>
				<label>
					C/V
					<select value={filtros.cv} onChange={(e) => atualizarFiltro("cv", e.target.value)}>
						<option value="">Todos</option>
						<option value="C">C (Compra)</option>
						<option value="V">V (Venda)</option>
						<option value="NA">NA</option>
					</select>
				</label>
				<label>
					Tipo Ordem
					<select value={filtros.tipo} onChange={(e) => atualizarFiltro("tipo", e.target.value)}>
						<option value="">Todos</option>
						<option value="Pronto">Pronto</option>
						<option value="Credito">Crédito</option>
						<option value="Virtual">Virtual</option>
					</select>
				</label>
				<label>
					Banco
					<input
						value={filtros.banco}
						onChange={(e) => atualizarFiltro("banco", e.target.value)}
						placeholder="Todos"
					/>
				</label>
				<label>
					Fundo
					<select value={filtros.fundo} onChange={(e) => atualizarFiltro("fundo", e.target.value)}>
						<option value="">Todos</option>
						{fundosDisponiveis.map((fundo) => (
							<option key={fundo} value={fundo}>
								{fundo}
							</option>
						))}
					</select>
				</label>
				<label>
					Criado por
					<input
						value={filtros.criadoPor}
						onChange={(e) => atualizarFiltro("criadoPor", e.target.value)}
						placeholder="Todos"
					/>
				</label>
				<label>
					Completado por
					<input
						value={filtros.completadoPor}
						onChange={(e) => atualizarFiltro("completadoPor", e.target.value)}
						placeholder="Todos"
					/>
				</label>
				{algumFiltroAtivo && (
					<button type="button" className="btn btn-secondary" onClick={() => setFiltros(FILTROS_VAZIOS)}>
						Limpar filtros
					</button>
				)}
			</div>

			{carregando ? (
				<LoadingState label="Carregando ordens…" />
			) : confirmadas.length === 0 ? (
				<EmptyState
					title="Nenhuma ordem confirmada"
					message={'Assim que uma ordem for confirmada na aba "Em andamento", ela aparece aqui.'}
				/>
			) : filtradas.length === 0 ? (
				<EmptyState title="Nenhuma ordem encontrada" message="Ajuste os filtros acima e tente novamente." />
			) : (
				<div className="table-card">
					<table>
						<thead>
							<tr>
								<th>Código da Ordem</th>
								<th>Código Operação Origem</th>
								<th>Data do Fechamento</th>
								<th>Cliente</th>
								<th>CNPJ/CPF</th>
								<th>Banco</th>
								<th>C/V</th>
								<th>Tipo Ordem</th>
								<th>Valor em moeda</th>
								<th>Spot Asset</th>
								<th>Nivelamento</th>
								<th>Taxa Final</th>
								<th>Fundo</th>
								<th>Moeda</th>
								<th>Valor em Real</th>
								<th>Total Bruto Câmbio</th>
								<th>Spread emissão</th>
								<th>Spread liquidação</th>
								<th>Custo</th>
								<th>Rebate</th>
								<th>Base de comissionamento</th>
								<th>Comissão Líquida</th>
								<th>Criado por</th>
								<th>Criado em</th>
								<th>Completado por</th>
								<th>Completado em</th>
							</tr>
						</thead>
						<tbody>
							{filtradas.map((op) => (
								<tr key={op.id}>
									<td className="mono">{op.idTrade}</td>
									<td className="mono">{op.codigoOperacao || "—"}</td>
									<td>{formatarData(op.data)}</td>
									<td>{op.clienteNome || `#${op.clienteId}`}</td>
									<td>{op.clienteDocumento || "—"}</td>
									<td>{op.bancoNome || `#${op.bancoId}`}</td>
									<td>{op.cv}</td>
									<td>{op.prCrVir}</td>
									<td className="mono">{formatarMoeda(op.valorMe)}</td>
									<td className="mono">{formatarMoeda(op.spotAsset)}</td>
									<td className="mono">{formatarMoeda(op.nivelamento)}</td>
									<td className="mono">{formatarMoeda(op.taxaFinal)}</td>
									<td>{op.fundo || calcularFundo(op.prCrVir)}</td>
									<td>{op.moeda}</td>
									<td className="mono">{formatarMoeda(op.reais)}</td>
									<td className="mono">{formatarMoeda(op.totalBrutoCambio)}</td>
									<td className="mono">{formatarSpreadEmissao(op.spreadEmissao)}</td>
									<td className="mono">{formatarPercentual(op.spreadLiquidacao)}</td>
									{/* Custo só é informado quando a ordem é Crédito (pedido do usuário) — pros
									    demais tipos a API devolve 0 fixo (Incremento 57), mas a tabela mostra "—". */}
									<td className="mono">
										{(op.prCrVir || "").toLowerCase() === "credito" ? formatarPercentual(op.custo) : "—"}
									</td>
									<td className="mono">{formatarMoeda(op.rebate)}</td>
									<td className="mono">{formatarMoeda(op.baseComissionamento)}</td>
									<td className="mono">{formatarMoeda(op.comissaoLiquida)}</td>
									<td>{op.criadoPorNome || "—"}</td>
									<td>{formatarDataHora(op.criadoEm)}</td>
									<td>{op.completadoPorNome || "—"}</td>
									<td>{formatarDataHora(op.completadoEm)}</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}
		</div>
	);
}
