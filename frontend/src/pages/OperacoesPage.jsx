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

// spreadEmissao e fundo não têm campo nenhum na tela (pedido do usuário) — sempre "NA"
// e "P" aqui, porque a tela só cria/edita operações com prCrVir "Pronto" (Crédito/Virtual
// só entram via API direto — e aí sim spreadEmissao precisa de um valor numérico real e
// fundo precisa ser "M" — ver docs/dominio.md e OperacaoService.validarFundo).
const FORM_VAZIO = {
	data: "",
	clienteId: "",
	bancoId: "",
	// Código da operação (Incremento 71): só números; opcional aqui porque a tela só cria
	// ordens "Pronto" — a obrigatoriedade (tipo Crédito) é validada pelo backend (400).
	codigoOperacao: "",
	cv: "",
	prCrVir: "Pronto",
	fundo: "P",
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
	// Seleção múltipla pra confirmar várias ordens de uma vez (pedido do usuário):
	// guarda os ids marcados; o "Selecionar todas" age sobre as linhas filtradas visíveis.
	const [selecionadas, setSelecionadas] = useState(new Set());
	const [confirmandoSelecionadas, setConfirmandoSelecionadas] = useState(false);

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
				codigoOperacao: form.codigoOperacao || null,
				clienteId: Number(form.clienteId),
				bancoId: Number(form.bancoId),
				valorMe: Number(form.valorMe),
				spotAsset: form.spotAsset === "" || form.spotAsset == null ? null : Number(form.spotAsset),
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
			codigoOperacao: op.codigoOperacao || "",
			cv: op.cv,
			prCrVir: op.prCrVir,
			// A tela não tem campo pra editar prCrVir, então fundo é recalculado a partir do
			// valor existente (não hardcoded "P") — pra continuar correto caso a ordem sendo
			// editada tenha sido criada via API como Crédito/Virtual (fundo "M").
			// Incremento 75: o Fundo agora é persistido (fora de Pronto aceita qualquer
			// letra via API) — reenvia o valor guardado; o fallback derivado só cobre
			// resposta antiga ainda em cache.
			fundo: op.fundo || calcularFundo(op.prCrVir),
			spreadEmissao: op.spreadEmissao,
			moeda: op.moeda,
			valorMe: String(op.valorMe),
			spotAsset: op.spotAsset == null ? "" : String(op.spotAsset),
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

	function alternarSelecao(id) {
		setSelecionadas((atual) => {
			const novo = new Set(atual);
			if (novo.has(id)) {
				novo.delete(id);
			} else {
				novo.add(id);
			}
			return novo;
		});
	}

	function alternarSelecaoTodas(visiveis, todasMarcadas) {
		setSelecionadas(todasMarcadas ? new Set() : new Set(visiveis.map((op) => op.id)));
	}

	async function confirmarSelecionadas(ids) {
		setConfirmandoSelecionadas(false);
		// Confirma uma a uma pelo mesmo endpoint da confirmação individual (mantém a
		// auditoria por ordem); se alguma falhar, o erro aparece no pop-up e as demais
		// seguem — a recarga no final mostra o que de fato foi confirmado.
		for (const id of ids) {
			try {
				await api.patch(`/operacoes/${id}/status`, { status: "CONFIRMADO" });
			} catch {
				// erro já mostrado como pop-up pelo api/client.js
			}
		}
		setSelecionadas(new Set());
		carregarTudo();
	}

	function atualizarFiltro(campo, valor) {
		setFiltros((atual) => ({ ...atual, [campo]: valor }));
	}

	const emAndamento = operacoes.filter((op) => op.status === "ANDAMENTO");

	// Moeda não tem domínio fechado no sistema (suporta qualquer ISO 4217) — as opções
	// do <select> vêm das moedas que já aparecem nas ordens em andamento carregadas,
	// mesmo critério usado na aba Confirmadas (Incremento 68).
	const moedasDisponiveis = [...new Set(emAndamento.map((op) => op.moeda).filter(Boolean))].sort();

	// Fundo deixou de ter domínio fechado P/M (Incremento 75) — as opções do filtro vêm
	// das letras que já aparecem nas ordens carregadas, mesmo critério das moedas.
	const fundosDisponiveis = [...new Set(emAndamento.map((op) => op.fundo || calcularFundo(op.prCrVir)).filter(Boolean))].sort();

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
		if (filtros.fundo && (op.fundo || calcularFundo(op.prCrVir)) !== filtros.fundo) return false;
		if (!contemTexto(op.criadoPorNome, filtros.criadoPor)) return false;
		if (!contemTexto(op.completadoPorNome, filtros.completadoPor)) return false;
		return true;
	});

	const algumFiltroAtivo = Object.values(filtros).some((v) => v !== "");

	// Só conta/age sobre ordens selecionadas que continuam visíveis com os filtros atuais
	// — o que o usuário vê marcado é exatamente o que o botão confirma.
	const idsSelecionadosVisiveis = filtradas.filter((op) => selecionadas.has(op.id)).map((op) => op.id);
	const todasVisiveisSelecionadas = filtradas.length > 0 && idsSelecionadosVisiveis.length === filtradas.length;

	return (
		<div>
			<h1>Ordens</h1>
			<p className="fechamento-periodo-legenda">
				Confirmar, editar e registrar ordens de câmbio. Ordens já confirmadas ficam na aba "Confirmadas".
			</p>

			{!ehConsultor && (
			<form onSubmit={salvar} className="form-operacao">
				<label>
					Data do Fechamento
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
					Código Operação Origem
					<input
						type="text"
						inputMode="numeric"
						pattern="\d*"
						title="Só números"
						value={form.codigoOperacao}
						onChange={(e) => setForm({ ...form, codigoOperacao: e.target.value.replace(/\D/g, "") })}
						placeholder="Opcional"
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
					Valor em moeda
					<input
						type="number"
						step="0.01"
						value={form.valorMe}
						onChange={(e) => setForm({ ...form, valorMe: e.target.value })}
						required
					/>
				</label>
				{/* Spot Asset não tem campo na tela (Incremento 72, pedido do usuário): entra
				    somente via API. Continua no estado do form (escondido) pra não apagar o
				    valor de uma ordem criada via API ao editá-la — mesmo padrão do prCrVir. */}
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
					Data do Fechamento
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
			<>
			{!ehConsultor && (
				<div style={{ margin: "12px 0" }}>
					<button
						type="button"
						className="btn btn-primary"
						disabled={idsSelecionadosVisiveis.length === 0}
						onClick={() => setConfirmandoSelecionadas(true)}
					>
						Confirmar selecionadas ({idsSelecionadosVisiveis.length})
					</button>
				</div>
			)}
			<div className="table-card">
			<table>
				<thead>
					<tr>
						{!ehConsultor && (
							<th>
								<input
									type="checkbox"
									title="Selecionar todas as ordens visíveis"
									checked={todasVisiveisSelecionadas}
									onChange={() => alternarSelecaoTodas(filtradas, todasVisiveisSelecionadas)}
								/>
							</th>
						)}
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
						<th></th>
					</tr>
				</thead>
				<tbody>
					{filtradas.map((op) => (
						<tr key={op.id}>
							{!ehConsultor && (
								<td>
									<input
										type="checkbox"
										checked={selecionadas.has(op.id)}
										onChange={() => alternarSelecao(op.id)}
									/>
								</td>
							)}
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
							{/* Campos calculados — desde o Incremento 76 existem em QUALQUER status
							    (em andamento são uma prévia que acompanha as edições; confirmar trava).
							    "—" só aparece quando não há fórmula pro caso (ex: Total Bruto com C/V
							    desconhecido) — docs/dominio.md. */}
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
		</>
		)}

		<ConfirmModal
			open={confirmandoSelecionadas}
			title="Confirmar ordens selecionadas"
			message={
				<>
					Confirma as <strong>{idsSelecionadosVisiveis.length}</strong> ordens selecionadas? Os valores
					calculados de cada uma ficam travados como estão e nenhuma delas pode voltar para "Em andamento".
				</>
			}
			confirmLabel="Confirmar todas"
			onConfirm={() => confirmarSelecionadas(idsSelecionadosVisiveis)}
			onCancel={() => setConfirmandoSelecionadas(false)}
		/>

		<ConfirmModal
			open={operacaoParaConfirmar !== null}
			title="Confirmar ordem"
			message={
				operacaoParaConfirmar && (
					<>
						Confirma a ordem <strong>{operacaoParaConfirmar.idTrade}</strong> (
						{operacaoParaConfirmar.clienteNome}, {operacaoParaConfirmar.moeda}{" "}
						{formatarMoeda(operacaoParaConfirmar.valorMe)})? Os valores calculados ficam travados como
						estão e a ordem não pode voltar para "Em andamento".
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
