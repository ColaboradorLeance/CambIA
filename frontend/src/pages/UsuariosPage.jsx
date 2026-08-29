import { useEffect, useState } from "react";
import { api } from "../api/client";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";

const FORM_VAZIO = { nome: "", email: "", perfil: "ANALISTA" };

const ROTULOS_PERFIL = {
	ADMIN: "Admin",
	ANALISTA: "Analista",
	CONSULTOR: "Consultor",
};

export default function UsuariosPage() {
	const [usuarios, setUsuarios] = useState([]);
	const [form, setForm] = useState(FORM_VAZIO);
	const [editandoId, setEditandoId] = useState(null);
	const [carregando, setCarregando] = useState(true);

	async function carregar() {
		setCarregando(true);
		try {
			setUsuarios(await api.get("/usuarios"));
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
				await api.put(`/usuarios/${editandoId}`, form);
			} else {
				await api.post("/usuarios", form);
			}
			cancelarEdicao();
			carregar();
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	function editar(usuario) {
		setEditandoId(usuario.id);
		setForm({ nome: usuario.nome, email: usuario.email, perfil: usuario.perfil });
	}

	function cancelarEdicao() {
		setEditandoId(null);
		setForm(FORM_VAZIO);
	}

	async function remover(id) {
		if (!window.confirm("Remover este usuário?")) return;
		try {
			await api.del(`/usuarios/${id}`);
			carregar();
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	return (
		<div>
			<h1>Usuários</h1>

			<form onSubmit={salvar} className="form-cadastro">
				<input
					placeholder="Nome"
					value={form.nome}
					onChange={(e) => setForm({ ...form, nome: e.target.value })}
					required
				/>
				<input
					placeholder="E-mail"
					type="email"
					value={form.email}
					onChange={(e) => setForm({ ...form, email: e.target.value })}
					required
				/>
				<select
					value={form.perfil}
					onChange={(e) => setForm({ ...form, perfil: e.target.value })}
				>
					<option value="ANALISTA">Analista</option>
					<option value="CONSULTOR">Consultor</option>
					<option value="ADMIN">Admin</option>
				</select>
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
				<LoadingState label="Carregando usuários…" />
			) : usuarios.length === 0 ? (
				<EmptyState
					title="Nenhum usuário cadastrado"
					message="Cadastre o primeiro usuário usando o formulário acima."
				/>
			) : (
				<div className="table-card">
					<table>
						<thead>
							<tr>
								<th>Nome</th>
								<th>E-mail</th>
								<th>Perfil</th>
								<th></th>
							</tr>
						</thead>
						<tbody>
							{usuarios.map((usuario) => (
								<tr key={usuario.id}>
									<td>{usuario.nome}</td>
									<td>{usuario.email}</td>
									<td>{ROTULOS_PERFIL[usuario.perfil] || usuario.perfil}</td>
									<td>
										<button className="btn btn-secondary btn-sm" onClick={() => editar(usuario)}>
											Editar
										</button>{" "}
										<button className="btn btn-danger btn-sm" onClick={() => remover(usuario.id)}>
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
