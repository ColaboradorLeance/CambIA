import { useEffect, useState } from "react";
import { api } from "../api/client";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";
import ConfirmModal from "../components/ConfirmModal";
import { ROTULOS_STATUS_CHAVE, rotulosEscopos } from "../utils/chavesApi";
import { formatarData, formatarDataHora } from "../utils/data";

// Cadastros → Chaves de API (só Admin). Desde o Incremento 81 (K7, docs/decisoes.md): o
// Admin vê TODAS as chaves, com o dono, e revoga qualquer uma (emergência: chave vazada de
// quem saiu ou está ausente), mas não cria chave para outros — cada usuário cria a sua em
// "Meu perfil".

export default function ChavesApiPage() {
	const [chaves, setChaves] = useState([]);
	const [carregando, setCarregando] = useState(true);
	const [chaveParaRevogar, setChaveParaRevogar] = useState(null);

	async function carregar() {
		setCarregando(true);
		try {
			setChaves(await api.get("/chaves-api"));
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		} finally {
			setCarregando(false);
		}
	}

	useEffect(() => {
		carregar();
	}, []);

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
				Todas as chaves de API do sistema. Cada usuário cria as próprias em <strong>Meu perfil</strong> (clique no
				nome, no menu lateral); aqui o Admin acompanha o uso e revoga qualquer chave em caso de vazamento.
			</p>

			{carregando ? (
				<LoadingState label="Carregando chaves…" />
			) : chaves.length === 0 ? (
				<EmptyState
					title="Nenhuma chave de API"
					message="Nenhum usuário criou chave ainda. Cada um cria as próprias em Meu perfil."
				/>
			) : (
				<div className="table-card">
					<table>
						<thead>
							<tr>
								<th>Nome</th>
								<th>Dono</th>
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
									<td>{chave.donoNome || `#${chave.donoId}`}</td>
									<td className="mono">{chave.prefixo}…</td>
									<td>{rotulosEscopos(chave.escopos)}</td>
									<td title={chave.criadaPorNome ? `Criada por ${chave.criadaPorNome}` : undefined}>
										{formatarDataHora(chave.criadaEm)}
									</td>
									<td>{formatarData(chave.expiraEm)}</td>
									<td>{chave.ultimoUsoEm ? formatarDataHora(chave.ultimoUsoEm) : "Nunca usada"}</td>
									<td title={chave.revogadaEm ? `Revogada por ${chave.revogadaPorNome || "—"}` : undefined}>
										{ROTULOS_STATUS_CHAVE[chave.status] || chave.status}
									</td>
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

			<ConfirmModal
				open={chaveParaRevogar !== null}
				title="Revogar chave de API"
				message={
					chaveParaRevogar && (
						<>
							Revogar a chave <strong>{chaveParaRevogar.nome}</strong> de{" "}
							<strong>{chaveParaRevogar.donoNome}</strong> (<span className="mono">{chaveParaRevogar.prefixo}…</span>
							)? Toda integração que a usa passa a receber 401 imediatamente. A revogação é definitiva — o dono
							precisa criar outra chave.
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
