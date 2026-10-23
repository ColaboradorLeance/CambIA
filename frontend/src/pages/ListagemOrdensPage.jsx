import { useEffect, useState } from "react";
import { api } from "../api/client";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";
import ModeloCalculo from "../components/ModeloCalculo";
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

const TAMANHO_PAGINA = 50;

// Espera a pessoa parar de digitar antes de consultar o servidor — sem isso, cada tecla
// num filtro de texto virava uma requisição.
const ESPERA_DIGITACAO_MS = 350;

const RESULTADO_VAZIO = { itens: [], total: 0, totalPaginas: 0 };

export default function ListagemOrdensPage() {
	// Incremento 80: paginação e filtros no servidor (GET /operacoes/pagina). Antes a tela
	// baixava TODAS as ordens e filtrava no navegador — custo que crescia com o histórico.
	const [filtros, setFiltros] = useState(FILTROS_VAZIOS);
	const [filtrosAplicados, setFiltrosAplicados] = useState(FILTROS_VAZIOS);
	const [pagina, setPagina] = useState(0);
	const [resultado, setResultado] = useState(RESULTADO_VAZIO);
	const [opcoes, setOpcoes] = useState({ moedas: [], fundos: [] });
	const [carregando, setCarregando] = useState(true);

	useEffect(() => {
		const espera = setTimeout(() => {
			setFiltrosAplicados(filtros);
			setPagina(0);
		}, ESPERA_DIGITACAO_MS);
		return () => clearTimeout(espera);
	}, [filtros]);

	useEffect(() => {
		// Ignora respostas que chegarem depois de outra consulta já ter sido disparada
		// (filtro mudou de novo no meio) — senão a tabela mostraria o resultado antigo.
		let atual = true;
		const parametros = new URLSearchParams({ status: "CONFIRMADO", pagina, tamanho: TAMANHO_PAGINA });
		Object.entries(filtrosAplicados).forEach(([campo, valor]) => {
			if (valor !== "") parametros.set(campo, valor);
		});
		async function carregar() {
			setCarregando(true);
			try {
				const resposta = await api.get(`/operacoes/pagina?${parametros}`);
				if (atual) setResultado(resposta);
			} catch {
				// erro já mostrado como pop-up pelo api/client.js
			} finally {
				if (atual) setCarregando(false);
			}
		}
		carregar();
		return () => {
			atual = false;
		};
	}, [filtrosAplicados, pagina]);

	useEffect(() => {
		// Moeda e Fundo não têm domínio fechado: as opções vêm das ordens confirmadas que
		// existem no banco (a página atual não tem todas).
		api.get("/operacoes/opcoes-filtro?status=CONFIRMADO")
			.then(setOpcoes)
			.catch(() => {
				// erro já mostrado como pop-up pelo api/client.js
			});
	}, []);

	function atualizarFiltro(campo, valor) {
		setFiltros((atual) => ({ ...atual, [campo]: valor }));
	}

	const moedasDisponiveis = opcoes.moedas;
	const fundosDisponiveis = opcoes.fundos;
	const filtradas = resultado.itens;
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

			{carregando && filtradas.length === 0 ? (
				<LoadingState label="Carregando ordens…" />
			) : filtradas.length === 0 && !algumFiltroAtivo ? (
				<EmptyState
					title="Nenhuma ordem confirmada"
					message={'Assim que uma ordem for confirmada na aba "Em andamento", ela aparece aqui.'}
				/>
			) : filtradas.length === 0 ? (
				<EmptyState title="Nenhuma ordem encontrada" message="Ajuste os filtros acima e tente novamente." />
			) : (
				<>
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
								<th>Modelo de cálculo</th>
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
									<td>
										<ModeloCalculo nome={op.calculoNome} formula={op.calculoFormula} />
									</td>
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
				<div className="paginacao" style={{ display: "flex", gap: 12, alignItems: "center", margin: "12px 0" }}>
					<button
						type="button"
						className="btn btn-secondary btn-sm"
						disabled={pagina === 0 || carregando}
						onClick={() => setPagina((p) => p - 1)}
					>
						Anterior
					</button>
					<span>
						Página {pagina + 1} de {Math.max(resultado.totalPaginas, 1)} · {resultado.total} ordens
					</span>
					<button
						type="button"
						className="btn btn-secondary btn-sm"
						disabled={pagina + 1 >= resultado.totalPaginas || carregando}
						onClick={() => setPagina((p) => p + 1)}
					>
						Próxima
					</button>
				</div>
				</>
			)}
		</div>
	);
}
