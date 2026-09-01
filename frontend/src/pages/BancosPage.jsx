import { useEffect, useState } from "react";
import { api } from "../api/client";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";

const FORM_VAZIO = { codigoBanco: "", sigla: "", nome: "", taxaRebate: "", calculoId: "" };

// Preenchimento automático do Nome a partir do código COMPE (3 dígitos) ou ISPB
// (8 dígitos) digitado em "Código do banco". Qualquer outro tamanho não dispara
// consulta nenhuma (mantém o comportamento de texto livre de sempre).
async function buscarNomePorCodigoBanco(codigo) {
	const somenteDigitos = (codigo || "").replace(/\D/g, "");
	if (somenteDigitos.length !== 3 && somenteDigitos.length !== 8) {
		return { nome: null, mensagem: null };
	}
	try {
		const resposta = await api.get(`/bancos/consulta-codigo/${somenteDigitos}`);
		if (resposta.nome) {
			return { nome: resposta.nome, mensagem: null };
		}
		const mensagem =
			somenteDigitos.length === 3
				? "Este código não participa do COMPE."
				: "ISPB não encontrado.";
		return { nome: null, mensagem };
	} catch {
		// Falha na consulta não deve atrapalhar o preenchimento manual — sem pop-up aqui.
		return { nome: null, mensagem: null };
	}
}

export default function BancosPage() {
	const [bancos, setBancos] = useState([]);
	const [calculos, setCalculos] = useState([]);
	const [form, setForm] = useState(FORM_VAZIO);
	const [editandoId, setEditandoId] = useState(null);
	const [carregando, setCarregando] = useState(true);
	const [mensagemCodigoBanco, setMensagemCodigoBanco] = useState(null);

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
				calculoId: Number(form.calculoId),
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
			calculoId: String(banco.calculoId),
		});
	}

	function cancelarEdicao() {
		setEditandoId(null);
		setForm(FORM_VAZIO);
		setMensagemCodigoBanco(null);
	}

	async function completarNomePeloCodigoBanco() {
		const { nome, mensagem } = await buscarNomePorCodigoBanco(form.codigoBanco);
		if (nome) {
			setForm((atual) => ({ ...atual, nome }));
		}
		setMensagemCodigoBanco(mensagem);
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
					onChange={(e) => {
						setForm({ ...form, codigoBanco: e.target.value });
						setMensagemCodigoBanco(null);
					}}
					onBlur={completarNomePeloCodigoBanco}
					required
				/>
				{mensagemCodigoBanco && <p className="campo-aviso">{mensagemCodigoBanco}</p>}
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
				<select
					value={form.calculoId}
					onChange={(e) => setForm({ ...form, calculoId: e.target.value })}
					required
				>
					<option value="" disabled>
						Selecione o cálculo
					</option>
					{calculos.map((c) => (
						<option key={c.id} value={c.id}>
							{c.nome}
						</option>
					))}
				</select>

				<div>
					<button type="submit" className="btn btn-primary" disabled={calculos.length === 0}>
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
								<th>Cálculo</th>
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
										{banco.calculoNome} <code>{banco.calculoFormula}</code>
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
