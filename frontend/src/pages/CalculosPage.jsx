import { useEffect, useState } from "react";
import { api } from "../api/client";
import FormulaBuilder from "../components/FormulaBuilder";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";

const FORM_VAZIO = { nome: "", formula: "" };

export default function CalculosPage() {
	const [calculos, setCalculos] = useState([]);
	const [form, setForm] = useState(FORM_VAZIO);
	const [editandoId, setEditandoId] = useState(null);
	const [carregando, setCarregando] = useState(true);

	async function carregar() {
		setCarregando(true);
		try {
			setCalculos(await api.get("/calculos"));
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
			const payload = { nome: form.nome, formula: form.formula };
			if (editandoId) {
				await api.put(`/calculos/${editandoId}`, payload);
			} else {
				await api.post("/calculos", payload);
			}
			cancelarEdicao();
			carregar();
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	function editar(calculo) {
		setEditandoId(calculo.id);
		setForm({ nome: calculo.nome, formula: calculo.formula });
	}

	function cancelarEdicao() {
		setEditandoId(null);
		setForm(FORM_VAZIO);
	}

	async function remover(id) {
		if (!window.confirm("Remover este cálculo?")) return;
		try {
			await api.del(`/calculos/${id}`);
			carregar();
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	return (
		<div>
			<h1>Modelos de Cálculo</h1>
			<p className="fechamento-periodo-legenda">
				Cadastre aqui os modelos de fórmula de comissão. Cada banco seleciona um modelo já cadastrado.
			</p>

			<form onSubmit={salvar} className="form-cadastro-banco">
				<input
					placeholder="Nome do modelo (ex: 70% com desconto)"
					value={form.nome}
					onChange={(e) => setForm({ ...form, nome: e.target.value })}
					required
				/>

				<label className="formula-label">Fórmula</label>
				<FormulaBuilder
					value={form.formula}
					onChange={(formula) => setForm({ ...form, formula })}
				/>

				<div>
					<button type="submit" className="btn btn-primary">
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
				<LoadingState label="Carregando modelos de cálculo…" />
			) : calculos.length === 0 ? (
				<EmptyState
					title="Nenhum modelo de cálculo cadastrado"
					message="Cadastre o primeiro modelo usando o formulário acima."
				/>
			) : (
				<div className="table-card">
					<table>
						<thead>
							<tr>
								<th>Nome</th>
								<th>Fórmula</th>
								<th></th>
							</tr>
						</thead>
						<tbody>
							{calculos.map((calculo) => (
								<tr key={calculo.id}>
									<td>{calculo.nome}</td>
									<td>
										<code>{calculo.formula}</code>
									</td>
									<td>
										<button className="btn btn-secondary btn-sm" onClick={() => editar(calculo)}>
											Editar
										</button>{" "}
										<button className="btn btn-danger btn-sm" onClick={() => remover(calculo.id)}>
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
