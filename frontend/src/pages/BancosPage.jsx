import { useEffect, useRef, useState } from "react";
import { api } from "../api/client";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";

const FORM_VAZIO = { codigoBanco: "", sigla: "", nome: "", taxaRebate: "", calculoIds: [] };

// Preenchimento automático do Nome a partir do código COMPE (3 dígitos) ou ISPB
// (8 dígitos) digitado em "Código do banco". Qualquer outro tamanho não dispara
// consulta nenhuma (mantém o comportamento de texto livre de sempre).
async function buscarNomePorCodigoBanco(codigo) {
	const somenteDigitos = (codigo || "").replace(/\D/g, "");
	if (somenteDigitos.length !== 3 && somenteDigitos.length !== 8) {
		return null;
	}
	try {
		const resposta = await api.get(`/bancos/consulta-codigo/${somenteDigitos}`);
		if (resposta.nome) {
			return { tipo: "encontrado", texto: `Encontrado: ${resposta.nome}`, nome: resposta.nome };
		}
		const texto =
			somenteDigitos.length === 3 ? "Este código não participa do COMPE." : "ISPB não encontrado.";
		return { tipo: "nao-encontrado", texto, nome: null };
	} catch {
		// Falha na consulta não deve atrapalhar o preenchimento manual — sem pop-up aqui.
		return null;
	}
}

export default function BancosPage() {
	const [bancos, setBancos] = useState([]);
	const [calculos, setCalculos] = useState([]);
	const [form, setForm] = useState(FORM_VAZIO);
	const [editandoId, setEditandoId] = useState(null);
	const [carregando, setCarregando] = useState(true);
	// null (nada a informar) | { tipo: "buscando" | "encontrado" | "nao-encontrado", texto }
	const [statusCodigoBanco, setStatusCodigoBanco] = useState(null);
	// Guarda qual código está sendo buscado no momento, pra descartar uma resposta que
	// chegou atrasada depois que o usuário já editou o campo de novo (evita que uma
	// busca antiga sobrescreva o Nome com o banco errado).
	const codigoEmBuscaRef = useRef(null);
	// Timer do debounce — busca sozinha meio segundo depois que a pessoa para de
	// digitar, sem precisar sair do campo (ver useEffect abaixo).
	const debounceRef = useRef(null);

	async function carregar() {
		setCarregando(true);
		try {
			const [ban, calc] = await Promise.all([api.get("/bancos"), api.get("/calculos")]);
			setBancos(ban);
			setCalculos(calc);
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		} finally {
			setCarregando(false);
		}
	}

	useEffect(() => {
		carregar();
	}, []);

	async function salvar(evento) {
		evento.preventDefault();
		try {
			const payload = {
				codigoBanco: form.codigoBanco,
				sigla: form.sigla,
				nome: form.nome,
				taxaRebate: Number(form.taxaRebate),
				calculoIds: form.calculoIds,
			};
			if (editandoId) {
				await api.put(`/bancos/${editandoId}`, payload);
			} else {
				await api.post("/bancos", payload);
			}
			cancelarEdicao();
			carregar();
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	function editar(banco) {
		setEditandoId(banco.id);
		setForm({
			codigoBanco: banco.codigoBanco,
			sigla: banco.sigla,
			nome: banco.nome,
			taxaRebate: String(banco.taxaRebate),
			calculoIds: banco.calculos.map((c) => c.id),
		});
	}

	// Incremento 85: inativo só aparece no banco que já o tinha (o backend recusa marcar um novo).
	const bancoEmEdicao = bancos.find((b) => b.id === editandoId);

	function alternarCalculo(id) {
		setForm((atual) => ({
			...atual,
			calculoIds: atual.calculoIds.includes(id)
				? atual.calculoIds.filter((c) => c !== id)
				: [...atual.calculoIds, id],
		}));
	}

	function cancelarEdicao() {
		setEditandoId(null);
		setForm(FORM_VAZIO);
		setStatusCodigoBanco(null);
	}

	// Toda edição no Código do banco (inclusive apagar) já limpa o Nome na hora —
	// garante que o Nome nunca fica associado a um código diferente do que está
	// digitado no momento. Só volta a ter um Nome depois de uma busca nova encontrar algo.
	// Só aceita números — qualquer outro caractere digitado é descartado na hora.
	function editarCodigoBanco(valor) {
		const somenteDigitos = valor.replace(/\D/g, "");
		clearTimeout(debounceRef.current);
		codigoEmBuscaRef.current = null; // invalida qualquer busca em andamento pro código anterior
		setForm((atual) => ({ ...atual, codigoBanco: somenteDigitos, nome: "" }));
		setStatusCodigoBanco(null);
	}

	async function buscarAgora() {
		const somenteDigitos = (form.codigoBanco || "").replace(/\D/g, "");
		if (somenteDigitos.length !== 3 && somenteDigitos.length !== 8) {
			codigoEmBuscaRef.current = null;
			setStatusCodigoBanco(null);
			return;
		}
		codigoEmBuscaRef.current = somenteDigitos;
		setStatusCodigoBanco({ tipo: "buscando", texto: "Buscando…" });
		const resultado = await buscarNomePorCodigoBanco(somenteDigitos);
		if (codigoEmBuscaRef.current !== somenteDigitos) {
			return; // o campo já mudou de novo enquanto buscava — descarta esse resultado velho
		}
		if (resultado?.nome) {
			setForm((atual) => ({ ...atual, nome: resultado.nome }));
		}
		setStatusCodigoBanco(resultado);
	}

	// Busca sozinha meio segundo depois que a pessoa para de digitar um código de 3 ou
	// 8 dígitos — não precisa sair do campo pra disparar a busca. Sair do campo antes
	// disso (onBlur, abaixo) ainda dispara na hora, sem esperar o meio segundo.
	useEffect(() => {
		const somenteDigitos = (form.codigoBanco || "").replace(/\D/g, "");
		if (somenteDigitos.length !== 3 && somenteDigitos.length !== 8) {
			return;
		}
		debounceRef.current = setTimeout(buscarAgora, 500);
		return () => clearTimeout(debounceRef.current);
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [form.codigoBanco]);

	function saiuDoCampoCodigoBanco() {
		clearTimeout(debounceRef.current);
		buscarAgora();
	}

	async function remover(id) {
		if (!window.confirm("Remover este banco?")) return;
		try {
			await api.del(`/bancos/${id}`);
			carregar();
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	return (
		<div>
			<h1>Bancos</h1>

			{calculos.length === 0 && !carregando && (
				<p className="fechamento-periodo-legenda">
					Nenhum modelo de cálculo cadastrado ainda. Cadastre um em "Modelos de Cálculo" antes de criar um banco.
				</p>
			)}

			<form onSubmit={salvar} className="form-cadastro-banco">
				<input
					placeholder="Código do banco (COMPE ou ISPB)"
					value={form.codigoBanco}
					onChange={(e) => editarCodigoBanco(e.target.value)}
					onBlur={saiuDoCampoCodigoBanco}
					inputMode="numeric"
					required
				/>
				{statusCodigoBanco && (
					<p className={`campo-status campo-status-${statusCodigoBanco.tipo}`}>
						{statusCodigoBanco.texto}
					</p>
				)}
				<input
					placeholder="Sigla"
					value={form.sigla}
					onChange={(e) => setForm({ ...form, sigla: e.target.value })}
					required
				/>
				<input
					placeholder="Nome do banco"
					value={form.nome}
					onChange={(e) => setForm({ ...form, nome: e.target.value })}
					required
				/>
				<input
					type="number"
					step="0.01"
					placeholder="Taxa de rebate (%)"
					value={form.taxaRebate}
					onChange={(e) => setForm({ ...form, taxaRebate: e.target.value })}
					required
				/>
				{/* Incremento 84: o banco pode ter mais de um modelo de cálculo; com mais de
				    um, a ordem escolhe qual usar. */}
				<fieldset className="banco-calculos">
					<legend>Modelos de cálculo do banco</legend>
					{calculos
						.filter((c) => c.ativo || bancoEmEdicao?.calculos.some((x) => x.id === c.id))
						.map((c) => (
							<label key={c.id}>
								<input
									type="checkbox"
									checked={form.calculoIds.includes(c.id)}
									onChange={() => alternarCalculo(c.id)}
								/>
								{c.nome} <code>{c.formula}</code>
								{!c.ativo && <span className="badge badge-neutral">Inativo</span>}
							</label>
						))}
				</fieldset>

				<div>
					<button
						type="submit"
						className="btn btn-primary"
						disabled={calculos.length === 0 || form.calculoIds.length === 0}
						title={form.calculoIds.length === 0 ? "Marque ao menos um modelo de cálculo" : undefined}
					>
						{editandoId ? "Atualizar" : "Adicionar"}
					</button>{" "}
					{editandoId && (
						<button type="button" className="btn btn-secondary" onClick={cancelarEdicao}>
							Cancelar
						</button>
					)}
				</div>
			</form>

			{carregando ? (
				<LoadingState label="Carregando bancos…" />
			) : bancos.length === 0 ? (
				<EmptyState
					title="Nenhum banco cadastrado"
					message="Cadastre o primeiro banco usando o formulário acima."
				/>
			) : (
				<div className="table-card">
					<table>
						<thead>
							<tr>
								<th>Código</th>
								<th>Sigla</th>
								<th>Nome</th>
								<th>Taxa de rebate</th>
								<th>Cálculos</th>
								<th></th>
							</tr>
						</thead>
						<tbody>
							{bancos.map((banco) => (
								<tr key={banco.id}>
									<td>{banco.codigoBanco}</td>
									<td>{banco.sigla}</td>
									<td>{banco.nome}</td>
									<td className="mono">{banco.taxaRebate}%</td>
									<td>
										{banco.calculos.map((c) => (
											<div key={c.id}>
												{c.nome} <code>{c.formula}</code>
												{!c.ativo && <span className="badge badge-neutral">Inativo</span>}
											</div>
										))}
									</td>
									<td>
										<button className="btn btn-secondary btn-sm" onClick={() => editar(banco)}>
											Editar
										</button>{" "}
										<button className="btn btn-danger btn-sm" onClick={() => remover(banco.id)}>
											Remover
										</button>
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}
		</div>
	);
}
