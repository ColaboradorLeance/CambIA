import { useEffect, useRef, useState } from "react";
import { api } from "../api/client";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";
import ConfirmModal from "../components/ConfirmModal";
import { formatarData, formatarDataHora } from "../utils/data";

// Incremento 79 — gestão de chaves de API (só Admin). Regras em docs/decisoes.md (D1–D10):
// a chave age em nome do usuário vinculado (nunca um Admin) e o segredo aparece UMA vez.
const FORM_VAZIO = { nome: "", usuarioId: "", expiraEm: "" };

function statusDaChave(chave) {
	if (chave.ativa) return "Ativa";
	return chave.revogadaEm ? "Revogada" : "Expirada";
}

// Modal próprio (não o ConfirmModal): este NÃO fecha com Esc nem com clique fora — o
// segredo nunca mais é exibido, e um clique acidental faria o Admin perder a chave.
function ChaveCriadaModal({ chave, onFechar }) {
	const campoRef = useRef(null);
	const [aviso, setAviso] = useState("");

	async function copiar() {
		try {
			await navigator.clipboard.writeText(chave.chave);
			setAviso("Chave copiada.");
		} catch {
			// Sem permissão de clipboard (ou contexto não seguro): seleciona o texto pra o
			// usuário copiar com Ctrl+C.
			campoRef.current?.select();
			setAviso("Não foi possível copiar automaticamente — o texto está selecionado, use Ctrl+C.");
		}
	}

	return (
		<div className="modal-overlay">
			<div className="modal-card" role="dialog" aria-modal="true">
				<h3>Chave criada: {chave.nome}</h3>
				<div className="modal-body">
					<p>
						<strong>Esta chave não será exibida novamente.</strong> Copie agora e guarde num cofre de
						senhas ou na configuração segura da integração.
					</p>
					<input
						ref={campoRef}
						className="mono"
						value={chave.chave}
						readOnly
						onFocus={(e) => e.target.select()}
						style={{ width: "100%" }}
					/>
					<p>
						Age em nome de <strong>{chave.usuarioNome}</strong>
						{chave.expiraEm ? `, válida até ${formatarData(chave.expiraEm)}` : ", sem data de expiração"}.
						Envie no header <span className="mono">X-API-Key</span>.
					</p>
					{aviso && <p>{aviso}</p>}
				</div>
				<div className="modal-footer">
					<button type="button" className="btn btn-secondary" onClick={onFechar}>
						Já guardei, fechar
					</button>
					<button type="button" className="btn btn-primary" onClick={copiar}>
						Copiar
					</button>
				</div>
			</div>
		</div>
	);
}

export default function ChavesApiPage() {
	const [chaves, setChaves] = useState([]);
	const [usuarios, setUsuarios] = useState([]);
	const [form, setForm] = useState(FORM_VAZIO);
	const [carregando, setCarregando] = useState(true);
	const [chaveCriada, setChaveCriada] = useState(null);
	const [chaveParaRevogar, setChaveParaRevogar] = useState(null);

	async function carregar() {
		setCarregando(true);
		try {
			const [listaChaves, listaUsuarios] = await Promise.all([api.get("/chaves-api"), api.get("/usuarios")]);
			setChaves(listaChaves);
			// D3: o backend recusa Admin; nem oferecer evita um erro previsível.
			setUsuarios(listaUsuarios.filter((u) => u.perfil !== "ADMIN"));
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		} finally {
			setCarregando(false);
		}
	}

	useEffect(() => {
		carregar();
	}, []);

	async function criar(evento) {
		evento.preventDefault();
		try {
			const criada = await api.post("/chaves-api", {
				nome: form.nome,
				usuarioId: Number(form.usuarioId),
				expiraEm: form.expiraEm || null,
			});
			setForm(FORM_VAZIO);
			setChaveCriada(criada);
			carregar();
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	async function revogar() {
		const id = chaveParaRevogar.id;
		setChaveParaRevogar(null);
		try {
			await api.del(`/chaves-api/${id}`);
			carregar();
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	return (
		<div>
			<h1>Chaves de API</h1>
			<p className="fechamento-periodo-legenda">
				Chaves para sistemas externos chamarem a API (header X-API-Key). Cada chave age em nome do usuário
				vinculado, com o perfil dele — recomendado um usuário técnico por integração.
			</p>

			<form onSubmit={criar} className="form-cadastro">
				<input
					placeholder="Nome (ex.: Integração Beyond — produção)"
					value={form.nome}
					maxLength={120}
					onChange={(e) => setForm({ ...form, nome: e.target.value })}
					required
				/>
				<select
					value={form.usuarioId}
					onChange={(e) => setForm({ ...form, usuarioId: e.target.value })}
					required
				>
					<option value="" disabled>
						Usuário vinculado
					</option>
					{usuarios.map((u) => (
						<option key={u.id} value={u.id}>
							{u.nome} ({u.perfil === "ANALISTA" ? "Analista" : "Consultor"})
						</option>
					))}
				</select>
				<input
					type="date"
					title="Expira em (opcional — vazio = não expira)"
					value={form.expiraEm}
					onChange={(e) => setForm({ ...form, expiraEm: e.target.value })}
				/>
				<button type="submit" className="btn btn-primary">
					Criar chave
				</button>
			</form>

			{carregando ? (
				<LoadingState label="Carregando chaves…" />
			) : chaves.length === 0 ? (
				<EmptyState
					title="Nenhuma chave de API"
					message="Crie a primeira chave usando o formulário acima."
				/>
			) : (
				<div className="table-card">
					<table>
						<thead>
							<tr>
								<th>Nome</th>
								<th>Prefixo</th>
								<th>Usuário</th>
								<th>Criada por</th>
								<th>Criada em</th>
								<th>Expira em</th>
								<th>Último uso</th>
								<th>Status</th>
								<th></th>
							</tr>
						</thead>
						<tbody>
							{chaves.map((chave) => (
								<tr key={chave.id}>
									<td>{chave.nome}</td>
									<td className="mono">{chave.prefixo}…</td>
									<td>{chave.usuarioNome || `#${chave.usuarioId}`}</td>
									<td>{chave.criadaPorNome || "—"}</td>
									<td>{formatarDataHora(chave.criadaEm)}</td>
									<td>{chave.expiraEm ? formatarData(chave.expiraEm) : "Não expira"}</td>
									<td>{chave.ultimoUsoEm ? formatarDataHora(chave.ultimoUsoEm) : "Nunca usada"}</td>
									<td title={chave.revogadaEm ? `Revogada por ${chave.revogadaPorNome || "—"}` : undefined}>
										{statusDaChave(chave)}
									</td>
									<td>
										{!chave.revogadaEm && (
											<button
												className="btn btn-danger btn-sm"
												onClick={() => setChaveParaRevogar(chave)}
											>
												Revogar
											</button>
										)}
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}

			{chaveCriada && <ChaveCriadaModal chave={chaveCriada} onFechar={() => setChaveCriada(null)} />}

			<ConfirmModal
				open={chaveParaRevogar !== null}
				title="Revogar chave de API"
				message={
					chaveParaRevogar && (
						<>
							Revogar a chave <strong>{chaveParaRevogar.nome}</strong> (
							<span className="mono">{chaveParaRevogar.prefixo}…</span>)? Toda integração que a usa passa a
							receber 401 imediatamente. A revogação é definitiva — para voltar, crie uma nova chave.
						</>
					)
				}
				confirmLabel="Revogar"
				cancelLabel="Voltar"
				perigo
				onConfirm={revogar}
				onCancel={() => setChaveParaRevogar(null)}
			/>
		</div>
	);
}
