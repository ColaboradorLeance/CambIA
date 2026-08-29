import { useEffect, useState } from "react";
import { api } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import LoadingState from "../components/LoadingState";
import { formatarData, formatarDataHora } from "../utils/data";

export default function HistoricoFechamentosPage() {
	const { usuario } = useAuth();
	const ehAdmin = usuario?.perfil === "ADMIN";

	const [horaExecucao, setHoraExecucao] = useState("");
	const [historico, setHistorico] = useState([]);
	const [usuarios, setUsuarios] = useState([]);
	const [destinatariosIds, setDestinatariosIds] = useState(new Set());
	const [mensagem, setMensagem] = useState("");
	const [mensagemDestinatarios, setMensagemDestinatarios] = useState("");
	const [carregando, setCarregando] = useState(true);

	async function carregar() {
		setCarregando(true);
		try {
			const configuracao = await api.get("/fechamentos/configuracao");
			setHoraExecucao(configuracao.horaExecucao ? configuracao.horaExecucao.slice(0, 5) : "");
			setHistorico(await api.get("/fechamentos/historico"));
			if (ehAdmin) {
				const [todosUsuarios, destinatarios] = await Promise.all([
					api.get("/usuarios"),
					api.get("/fechamentos/destinatarios"),
				]);
				setUsuarios(todosUsuarios);
				setDestinatariosIds(new Set(destinatarios.map((d) => d.usuarioId)));
			}
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		} finally {
			setCarregando(false);
		}
	}

	useEffect(() => {
		carregar();
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	async function salvarHorario(e) {
		e.preventDefault();
		setMensagem("");
		try {
			await api.put("/fechamentos/configuracao", { horaExecucao: `${horaExecucao}:00` });
			setMensagem("Horário salvo com sucesso.");
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	function alternarDestinatario(usuarioId) {
		setMensagemDestinatarios("");
		setDestinatariosIds((atual) => {
			const novo = new Set(atual);
			if (novo.has(usuarioId)) {
				novo.delete(usuarioId);
			} else {
				novo.add(usuarioId);
			}
			return novo;
		});
	}

	async function salvarDestinatarios() {
		setMensagemDestinatarios("");
		try {
			await api.put("/fechamentos/destinatarios", { usuarioIds: Array.from(destinatariosIds) });
			setMensagemDestinatarios("Destinatários salvos com sucesso.");
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	async function baixar(item) {
		const nomeArquivo = `fechamento-${item.data}.${item.formato.toLowerCase()}`;
		try {
			await api.baixarArquivo(`/fechamentos/historico/${item.id}/download`, nomeArquivo);
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		}
	}

	return (
		<div>
			<h1>Histórico de Fechamentos</h1>

			{ehAdmin && (
				<div className="fechamento-bloco">
					<h3>Geração automática</h3>
					<form onSubmit={salvarHorario} className="fechamento-agendamento-form">
						<label>
							Horário diário de geração
							<input
								type="time"
								value={horaExecucao}
								onChange={(e) => setHoraExecucao(e.target.value)}
								required
							/>
						</label>
						<button type="submit" className="btn btn-primary">
							Salvar
						</button>
					</form>
					{mensagem && <p className="fechamento-sucesso">{mensagem}</p>}

					<h4 className="comparativo-subtitulo-volume">Destinatários do e-mail diário</h4>
					<p className="fechamento-periodo-legenda">
						Marque quem deve receber o PDF e o Excel do fechamento por e-mail, todos os dias, assim que forem
						gerados. Só é enviado de verdade se o envio de e-mail estiver habilitado no servidor.
					</p>
					{usuarios.length === 0 ? (
						<p className="fechamento-vazio">Nenhum usuário cadastrado.</p>
					) : (
						<ul className="fechamento-destinatarios-lista">
							{usuarios.map((u) => (
								<li key={u.id}>
									<label>
										<input
											type="checkbox"
											checked={destinatariosIds.has(u.id)}
											onChange={() => alternarDestinatario(u.id)}
										/>
										{u.nome} <span className="fechamento-destinatario-email">({u.email})</span>
									</label>
								</li>
							))}
						</ul>
					)}
					<button type="button" className="btn btn-primary" onClick={salvarDestinatarios}>
						Salvar destinatários
					</button>
					{mensagemDestinatarios && <p className="fechamento-sucesso">{mensagemDestinatarios}</p>}
				</div>
			)}

			<div className="fechamento-bloco">
				<h3>Arquivos gerados</h3>
				{carregando ? (
					<LoadingState label="Carregando histórico…" />
				) : historico.length === 0 ? (
					<p className="fechamento-vazio">Nenhum fechamento gerado ainda.</p>
				) : (
					<table>
						<thead>
							<tr>
								<th>Data</th>
								<th>Formato</th>
								<th>Gerado em</th>
								<th></th>
							</tr>
						</thead>
						<tbody>
							{historico.map((item) => (
								<tr key={item.id}>
									<td>{formatarData(item.data)}</td>
									<td>{item.formato}</td>
									<td>{formatarDataHora(item.geradoEm)}</td>
									<td>
										<button type="button" className="btn btn-secondary btn-sm" onClick={() => baixar(item)}>
											Baixar
										</button>
									</td>
								</tr>
							))}
						</tbody>
					</table>
				)}
			</div>
		</div>
	);
}
