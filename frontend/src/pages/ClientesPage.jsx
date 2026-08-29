import { useEffect, useState } from "react";
import { api } from "../api/client";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";

const FORM_VAZIO = { nome: "", documento: "" };

export default function ClientesPage() {
	const [clientes, setClientes] = useState([]);
	const [form, setForm] = useState(FORM_VAZIO);
	const [editandoId, setEditandoId] = useState(null);
	const [carregando, setCarregando] = useState(true);

	async function carregar() {
		setCarregando(true);
		try {
			setClientes(await api.get("/clientes"));
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
			if (editandoId) {
				await api.put(`/clientes/${editandoId}`, form);
			} else {
				await api.post("/clientes", form);
			}
			cancelarEdicao();
			carregar();
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	function editar(cliente) {
		setEditandoId(cliente.id);
		setForm({ nome: cliente.nome, documento: cliente.documento });
	}

	function cancelarEdicao() {
		setEditandoId(null);
		setForm(FORM_VAZIO);
	}

	async function remover(id) {
		if (!window.confirm("Remover este cliente?")) return;
		try {
			await api.del(`/clientes/${id}`);
			carregar();
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	return (
		<div>
			<h1>Clientes</h1>

			<form onSubmit={salvar} className="form-cadastro">
				<input
					placeholder="Nome"
					value={form.nome}
					onChange={(e) => setForm({ ...form, nome: e.target.value })}
					required
				/>
				<input
					placeholder="Documento (CPF/CNPJ)"
					value={form.documento}
					onChange={(e) => setForm({ ...form, documento: e.target.value })}
					required
				/>
				<button type="submit" className="btn btn-primary">
					{editandoId ? "Atualizar" : "Adicionar"}
				</button>
				{editandoId && (
					<button type="button" className="btn btn-secondary" onClick={cancelarEdicao}>
						Cancelar
					</button>
				)}
			</form>

			{carregando ? (
				<LoadingState label="Carregando clientes…" />
			) : clientes.length === 0 ? (
				<EmptyState
					title="Nenhum cliente cadastrado"
					message="Cadastre o primeiro cliente usando o formulário acima."
				/>
			) : (
				<div className="table-card">
					<table>
						<thead>
							<tr>
								<th>Nome</th>
								<th>Documento</th>
								<th></th>
							</tr>
						</thead>
						<tbody>
							{clientes.map((cliente) => (
								<tr key={cliente.id}>
									<td>{cliente.nome}</td>
									<td>{cliente.documento}</td>
									<td>
										<button className="btn btn-secondary btn-sm" onClick={() => editar(cliente)}>
											Editar
										</button>{" "}
										<button className="btn btn-danger btn-sm" onClick={() => remover(cliente.id)}>
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
