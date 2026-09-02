import { useEffect, useState } from "react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";
import ConfirmModal from "../components/ConfirmModal";
import CurrencyPicker from "../components/CurrencyPicker";
import SearchPicker from "../components/SearchPicker";
import { formatarData, formatarDataHora } from "../utils/data";
import { formatarMoeda, formatarPercentual, formatarSpreadEmissao, calcularFundo } from "../utils/formatacao";

// spreadEmissao não tem campo nenhum na tela (pedido do usuário) — sempre "NA" aqui,
// porque a tela só cria/edita operações com prCrVir "Pronto" (Crédito/Virtual só entram
// via API direto, e aí sim precisam de um valor numérico real — ver docs/dominio.md).
const FORM_VAZIO = {
	data: "",
	clienteId: "",
	bancoId: "",
	cv: "",
	prCrVir: "Pronto",
	spreadEmissao: "NA",
	moeda: "",
	valorMe: "",
	spotAsset: "",
	nivelamento: "",
	taxaFinal: "",
};

const FILTROS_VAZIOS = {
	data: "",
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

function contemTexto(valor, filtro) {
	if (!filtro) return true;
	return (valor || "").toLowerCase().includes(filtro.toLowerCase());
}

export default function OperacoesPage() {
	const { usuario } = useAuth();
	const ehConsultor = usuario?.perfil === "CONSULTOR";
	const [operacoes, setOperacoes] = useState([]);
	const [clientes, setClientes] = useState([]);
	const [bancos, setBancos] = useState([]);
	const [form, setForm] = useState(FORM_VAZIO);
	const [filtros, setFiltros] = useState(FILTROS_VAZIOS);
	const [editandoId, setEditandoId] = useState(null);
	const [carregando, setCarregando] = useState(true);
	const [operacaoParaConfirmar, setOperacaoParaConfirmar] = useState(null);
	const [operacaoParaCancelar, setOperacaoParaCancelar] = useState(null);

	async function carregarTudo() {
		setCarregando(true);
		try {
			if (ehConsultor) {
				setOperacoes(await api.get("/operacoes"));
			} else {
				const [op, cli, ban] = await Promise.all([
					api.get("/operacoes"),
					api.get("/clientes"),
					api.get("/bancos"),
				]);
				setOperacoes(op);
				setClientes(cli);
				setBancos(ban);
			}
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		} finally {
			setCarregando(false);
		}
	}

	useEffect(() => {
		carregarTudo();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	async function salvar(evento) {
		evento.preventDefault();
		try {
			const payload = {
				...form,
				clienteId: Number(form.clienteId),
				bancoId: Number(form.bancoId),
				valorMe: Number(form.valorMe),
				spotAsset: Number(form.spotAsset),
				nivelamento: Number(form.nivelamento),
				taxaFinal: Number(form.taxaFinal),
			};
			if (editandoId) {
				await api.put(`/operacoes/${editandoId}`, payload);
			} else {
				await api.post("/operacoes", payload);
			}
			cancelarEdicao();
			carregarTudo();
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	function editar(op) {
		setEditandoId(op.id);
		setForm({
			data: op.data,
			clienteId: String(op.clienteId),
			bancoId: String(op.bancoId),
			cv: op.cv,
			prCrVir: op.prCrVir,
			spreadEmissao: op.spreadEmissao,
			moeda: op.moeda,
			valorMe: String(op.valorMe),
			spotAsset: String(op.spotAsset),
			nivelamento: String(op.nivelamento),
			taxaFinal: String(op.taxaFinal),
		});
		window.scrollTo({ top: 0, behavior: "smooth" });
	}

	function cancelarEdicao() {
		setEditandoId(null);
		setForm(FORM_VAZIO);
	}

	async function mudarStatus(id, status) {
		try {
			await api.patch(`/operacoes/${id}/status`, { status });
			carregarTudo();
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	async function confirmarOrdem() {
		const id = operacaoParaConfirmar.id;
		setOperacaoParaConfirmar(null);
		await mudarStatus(id, "CONFIRMADO");
	}

	async function cancelarOrdem() {
		const id = operacaoParaCancelar.id;
		setOperacaoParaCancelar(null);
		await mudarStatus(id, "CANCELADO");
	}

	function atualizarFiltro(campo, valor) {
		setFiltros((atual) => ({ ...atual, [campo]: valor }));
	}

	const emAndamento = operacoes.filter((op) => op.status === "ANDAMENTO");

	// Moeda não tem domínio fechado no sistema (suporta qualquer ISO 4217) — as opções
	// do <select> vêm das moedas que já aparecem nas ordens em andamento carregadas,
	// mesmo critério usado na aba Confirmadas (Incremento 68).
	const moedasDisponiveis = [...new Set(emAndamento.map((op) => op.moeda).filter(Boolean))].sort();

	const filtradas = emAndamento.filter((op) => {
		if (filtros.data && op.data !== filtros.data) return false;
		if (filtros.moeda && op.moeda !== filtros.moeda) return false;
		if (filtros.valorMoeda && !formatarMoeda(op.valorMe).includes(filtros.valorMoeda)) return false;
		if (!contemTexto(op.clienteDocumento, filtros.cnpj)) return false;
		if (!contemTexto(op.clienteNome, filtros.nome)) return false;
		if (!contemTexto(op.idTrade, filtros.idTrade)) return false;
		if (filtros.cv && op.cv !== filtros.cv) return false;
		if (filtros.tipo && (op.prCrVir || "").toLowerCase() !== filtros.tipo.toLowerCase()) return false;
		if (!contemTexto(op.bancoNome, filtros.banco)) return false;
		if (filtros.fundo && calcularFundo(op.prCrVir) !== filtros.fundo) return false;
		if (!contemTexto(op.criadoPorNome, filtros.criadoPor)) return false;
		if (!contemTexto(op.completadoPorNome, filtros.completadoPor)) return false;
		return true;
	});

	const algumFiltroAtivo = Object.values(filtros).some((v) => v !== "");

	return (
		<div>
			<h1>Ordens</h1>
			<p className="fechamento-periodo-legenda">
				Confirmar, editar e registrar ordens de câmbio. Ordens já confirmadas ficam na aba "Confirmadas".
			</p>

			{!ehConsultor && (
			<form onSubmit={salvar} className="form-operacao">
				<label>
					Data
					<input
						type="date"
						value={form.data}
						onChange={(e) => setForm({ ...form, data: e.target.value })}
						disabled={!!editandoId}
						title={editandoId ? "A data não pode ser alterada depois que a ordem é registrada" : undefined}
						required
					/>
				</label>
				<label>
					Cliente
					<SearchPicker
						items={clientes.map((c) => ({ id: c.id, label: c.nome }))}
						value={form.clienteId}
						onChange={(id) => setForm({ ...form, clienteId: String(id) })}
						placeholder="Buscar cliente"
						required
					/>
				</label>
				<label>
					Banco
					<SearchPicker
						items={bancos.map((b) => ({ id: b.id, label: b.nome }))}
						value={form.bancoId}
						onChange={(id) => setForm({ ...form, bancoId: String(id) })}
						placeholder="Buscar banco"
						required
					/>
				</label>
				<label>
					C/V
					<select
						value={form.cv}
						onChange={(e) => setForm({ ...form, cv: e.target.value })}
						required
					>
						<option value="" disabled>
							Selecione
						</option>
						<option value="C">C (Compra)</option>
						<option value="V">V (Venda)</option>
					</select>
				</label>
				<label>
					Moeda
					<CurrencyPicker
						value={form.moeda}
						onChange={(codigo) => setForm({ ...form, moeda: codigo })}
						required
					/>
				</label>
				<label>
					Valor em ME
					<input
						type="number"
						step="0.01"
						value={form.valorMe}
						onChange={(e) => setForm({ ...form, valorMe: e.target.value })}
						required
					/>
				</label>
				<label>
					Spot Asset
					<input
						type="number"
						step="0.0001"
						value={form.spotAsset}
						onChange={(e) => setForm({ ...form, spotAsset: e.target.value })}
						required
					/>
				</label>
				<label>
					Nivelamento
					<input
						type="number"
						step="0.0001"
						value={form.nivelamento}
						onChange={(e) => setForm({ ...form, nivelamento: e.target.value })}
						required
					/>
				</label>
				<label>
					Taxa Final
					<input
						type="number"
						step="0.0001"
						value={form.taxaFinal}
						onChange={(e) => setForm({ ...form, taxaFinal: e.target.value })}
						required
					/>
				</label>
				<button type="submit" className="btn btn-primary">
					{editandoId ? "Salvar alterações" : "Registrar ordem"}
				</button>
				{editandoId && (
					<button type="button" className="btn btn-secondary" onClick={cancelarEdicao}>
						Cancelar
					</button>
				)}
			</form>
			)}

			<div className="relatorio-filtros">
				<label>
					Data
					<input type="date" value={filtros.data} onChange={(e) => atualizarFiltro("data", e.target.value)} />
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
					Valor Moeda
					<input
						value={filtros.valorMoeda}
						onChange={(e) => atualizarFiltro("valorMoeda", e.target.value)}
						placeholder="Todos"
					/>
				</label>
				<label>
					CNPJ
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
					ID do trade
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
					Tipo
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
						<option value="P">P</option>
						<option value="M">M</option>
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
			) : emAndamento.length === 0 ? (
				<EmptyState
					title="Nenhuma ordem em andamento"
					message={
						ehConsultor
							? "Nenhuma ordem em andamento no momento."
							: "Registre uma nova ordem usando o formulário acima, ou veja as já confirmadas na aba \"Confirmadas\"."
					}
				/>
			) : filtradas.length === 0 ? (
				<EmptyState title="Nenhuma ordem encontrada" message="Ajuste os filtros acima e tente novamente." />
			) : (
			<div className="table-card">
			<table>
				<thead>
					<tr>
						<th>ID do trade</th>
						<th>Data</th>
						<th>Cliente</th>
						<th>CNPJ</th>
						<th>Banco</th>
						<th>C/V</th>
						<th>Tipo</th>
						<th>Fundo</th>
						<th>Moeda</th>
						<th>Valor ME</th>
						<th>R$</th>
						<th>Total Bruto</th>
						<th>Valor Absoluto</th>
						<th>Spread emissão</th>
						<th>Spread liquidação</th>
						<th>Custo</th>
						<th>Rebate</th>
						<th>Base de comissionamento</th>
						<th>Comissão</th>
						<th>Criado por</th>
						<th>Completado por</th>
						<th>Completado em</th>
						<th></th>
					</tr>
				</thead>
				<tbody>
					{filtradas.map((op) => (
						<tr key={op.id}>
							<td className="mono">{op.idTrade}</td>
							<td>{formatarData(op.data)}</td>
							<td>{op.clienteNome || `#${op.clienteId}`}</td>
							<td>{op.clienteDocumento || "—"}</td>
							<td>{op.bancoNome || `#${op.bancoId}`}</td>
							<td>{op.cv}</td>
							<td>{op.prCrVir}</td>
							<td>{calcularFundo(op.prCrVir)}</td>
							<td>{op.moeda}</td>
							<td className="mono">{formatarMoeda(op.valorMe)}</td>
							{/* Campos calculados — ficam vazios ("—") enquanto a ordem está Em andamento;
							    só existem depois de Confirmada (docs/dominio.md) — mesma coluna, mesmo
							    formatador da aba Confirmadas, só o valor que ainda não existe. */}
							<td className="mono">{formatarMoeda(op.reais)}</td>
							<td className="mono">{formatarMoeda(op.totalBrutoCambio)}</td>
							<td className="mono">{formatarMoeda(op.valorAbsoluto)}</td>
							<td className="mono">{formatarSpreadEmissao(op.spreadEmissao)}</td>
							<td className="mono">{formatarPercentual(op.spreadLiquidacao)}</td>
							<td className="mono">{formatarPercentual(op.custo)}</td>
							<td className="mono">{formatarMoeda(op.rebate)}</td>
							<td className="mono">{formatarMoeda(op.baseComissionamento)}</td>
							<td className="mono">{formatarMoeda(op.comissaoLiquida)}</td>
							<td>{op.criadoPorNome || "—"}</td>
							<td>{op.completadoPorNome || "—"}</td>
							<td>{formatarDataHora(op.completadoEm)}</td>
							<td>
								{!ehConsultor && (
									<>
										<button className="btn btn-secondary btn-sm" onClick={() => editar(op)}>
											Editar
										</button>{" "}
										<button
											className="btn btn-secondary btn-sm"
											onClick={() => setOperacaoParaConfirmar(op)}
										>
											Confirmar
										</button>{" "}
										<button
											className="btn btn-danger btn-sm"
											onClick={() => setOperacaoParaCancelar(op)}
										>
											Cancelar
										</button>
									</>
								)}
							</td>
						</tr>
					))}
				</tbody>
			</table>
		</div>
		)}

		<ConfirmModal
			open={operacaoParaConfirmar !== null}
			title="Confirmar ordem"
			message={
				operacaoParaConfirmar && (
					<>
						Confirma a ordem <strong>{operacaoParaConfirmar.idTrade}</strong> (
						{operacaoParaConfirmar.clienteNome}, {operacaoParaConfirmar.moeda}{" "}
						{formatarMoeda(operacaoParaConfirmar.valorMe)})? Os valores calculados (R$, Total Bruto e
						Comissão) passam a existir a partir de agora e a ordem não pode voltar para "Em andamento".
					</>
				)
			}
			confirmLabel="Confirmar"
			onConfirm={confirmarOrdem}
			onCancel={() => setOperacaoParaConfirmar(null)}
		/>

		<ConfirmModal
			open={operacaoParaCancelar !== null}
			title="Cancelar ordem"
			message={
				operacaoParaCancelar && (
					<>
						Confirma cancelar a ordem <strong>{operacaoParaCancelar.idTrade}</strong> (
						{operacaoParaCancelar.clienteNome}, {operacaoParaCancelar.moeda}{" "}
						{formatarMoeda(operacaoParaCancelar.valorMe)})? Esta ação não pode ser desfeita — a ordem
						cancelada não volta para "Em andamento" nem pode ser confirmada depois.
					</>
				)
			}
			confirmLabel="Cancelar ordem"
			cancelLabel="Voltar"
			perigo
			onConfirm={cancelarOrdem}
			onCancel={() => setOperacaoParaCancelar(null)}
		/>
		</div>
	);
}
