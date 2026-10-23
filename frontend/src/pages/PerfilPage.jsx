import { useEffect, useState } from "react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";
import ConfirmModal from "../components/ConfirmModal";
import ChaveCriadaModal from "../components/ChaveCriadaModal";
import { ROTULOS_ESCOPO, ROTULOS_STATUS_CHAVE, rotulosEscopos } from "../utils/chavesApi";
import { formatarData, formatarDataHora } from "../utils/data";

// Incremento 81 — "Meu perfil → Chaves de API" (docs/chaves-api-usuario-plano.md, K1–K6):
// cada usuário cria, lista e revoga só as próprias chaves; a chave age em nome dele, com
// escopos (consultar e/ou criar ordens) e expiração obrigatória de até 1 ano.

const ROTULOS_PERFIL = { ADMIN: "Administrador", ANALISTA: "Analista", CONSULTOR: "Consultor" };

function hojeIso() {
	// Data local (o backend valida no fuso de São Paulo, o mesmo dos usuários).
	const agora = new Date();
	return new Date(agora.getTime() - agora.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

function daquiAUmAnoIso() {
	const hoje = hojeIso();
	return `${Number(hoje.slice(0, 4)) + 1}${hoje.slice(4)}`;
}

function formVazio(consultor) {
	return { nome: "", leitura: true, criarOrdens: !consultor, expiraEm: "" };
}

export default function PerfilPage() {
	const { usuario } = useAuth();
	const consultor = usuario?.perfil === "CONSULTOR";
	const [chaves, setChaves] = useState([]);
	const [form, setForm] = useState(() => formVazio(consultor));
	const [carregando, setCarregando] = useState(true);
	const [chaveCriada, setChaveCriada] = useState(null);
	const [chaveParaRevogar, setChaveParaRevogar] = useState(null);

	async function carregar() {
		setCarregando(true);
		try {
			setChaves(await api.get("/perfil/chaves-api"));
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		} finally {
			setCarregando(false);
		}
	}

	useEffect(() => {
		carregar();
	}, []);

	const escoposEscolhidos = [form.leitura && "LEITURA", form.criarOrdens && "CRIAR_ORDENS"].filter(Boolean);

	async function criar(evento) {
		evento.preventDefault();
		try {
			const criada = await api.post("/perfil/chaves-api", {
				nome: form.nome,
				escopos: escoposEscolhidos,
				// Vazio = 1 ano (K5): o backend aplica o padrão.
				expiraEm: form.expiraEm || null,
			});
			setForm(formVazio(consultor));
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
			await api.del(`/perfil/chaves-api/${id}`);
			carregar();
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	return (
		<div>
			<h1>Meu perfil</h1>
			<p className="fechamento-periodo-legenda">
				{usuario?.nome} • {usuario?.email} • {ROTULOS_PERFIL[usuario?.perfil] || usuario?.perfil}
			</p>

			<h2>Chaves de API</h2>
			<p className="fechamento-periodo-legenda">
				Para um sistema seu chamar a API do CambIA em seu nome (header <span className="mono">X-API-Key</span>). Use
				uma chave por integração, guarde num cofre de senhas e revogue se vazar. A chave só consulta e cria ordens —
				a confirmação da ordem é feita aqui na tela.
			</p>

			<form onSubmit={criar} className="form-cadastro">
				<input
					placeholder="Nome (ex.: Integração ERP, produção)"
					value={form.nome}
					maxLength={120}
					onChange={(e) => setForm({ ...form, nome: e.target.value })}
					required
				/>
				<label>
					<input
						type="checkbox"
						checked={form.leitura}
						onChange={(e) => setForm({ ...form, leitura: e.target.checked })}
					/>{" "}
					{ROTULOS_ESCOPO.LEITURA}
				</label>
				<label
					title={consultor ? "O perfil Consultor só consulta — não pode ter chave que cria ordens." : undefined}
				>
					<input
						type="checkbox"
						checked={form.criarOrdens}
						disabled={consultor}
						onChange={(e) => setForm({ ...form, criarOrdens: e.target.checked })}
					/>{" "}
					{ROTULOS_ESCOPO.CRIAR_ORDENS}
					{consultor && " (indisponível para Consultor)"}
				</label>
				<input
					type="date"
					title="Expira em (vazio = daqui a 1 ano; no máximo 1 ano)"
					value={form.expiraEm}
					min={hojeIso()}
					max={daquiAUmAnoIso()}
					onChange={(e) => setForm({ ...form, expiraEm: e.target.value })}
				/>
				<button type="submit" className="btn btn-primary" disabled={escoposEscolhidos.length === 0}>
					Criar chave
				</button>
			</form>

			{carregando ? (
				<LoadingState label="Carregando chaves…" />
			) : chaves.length === 0 ? (
				<EmptyState title="Nenhuma chave de API" message="Crie a sua primeira chave usando o formulário acima." />
			) : (
				<div className="table-card">
					<table>
						<thead>
							<tr>
								<th>Nome</th>
								<th>Prefixo</th>
								<th>Escopos</th>
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
									<td>{rotulosEscopos(chave.escopos)}</td>
									<td>{formatarDataHora(chave.criadaEm)}</td>
									<td>{formatarData(chave.expiraEm)}</td>
									<td>{chave.ultimoUsoEm ? formatarDataHora(chave.ultimoUsoEm) : "Nunca usada"}</td>
									<td>{ROTULOS_STATUS_CHAVE[chave.status] || chave.status}</td>
									<td>
										{chave.status === "ATIVA" && (
											<button className="btn btn-danger btn-sm" onClick={() => setChaveParaRevogar(chave)}>
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
