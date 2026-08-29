import { useEffect, useState } from "react";
import { api } from "../api/client";
import LoadingState from "../components/LoadingState";
import EmptyState from "../components/EmptyState";
import { formatarDataHora } from "../utils/data";

const ROTULOS_TIPO = {
	CRIADA: { texto: "Registrada", classe: "badge-neutral" },
	EDITADA: { texto: "Editada", classe: "badge-warning" },
	CONFIRMADA: { texto: "Confirmada", classe: "badge-success" },
	CANCELADA: { texto: "Cancelada", classe: "badge-danger" },
};

function formatarMoeda(valor) {
	if (valor === null || valor === undefined) return "—";
	return Number(valor).toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 6 });
}

function descreverSnapshot(snapshot) {
	if (!snapshot) return "—";
	const cliente = snapshot.clienteNome || `#${snapshot.clienteId}`;
	const banco = snapshot.bancoNome || `#${snapshot.bancoId}`;
	return `${snapshot.cv} ${snapshot.moeda} ${formatarMoeda(snapshot.valorMe)} · ${cliente} · ${banco} · taxa final ${formatarMoeda(snapshot.taxaFinal)}`;
}

export default function HistoricoOperacoesPage() {
	const [eventos, setEventos] = useState([]);
	const [carregando, setCarregando] = useState(true);

	useEffect(() => {
		async function carregar() {
			setCarregando(true);
			try {
				setEventos(await api.get("/operacoes/historico"));
			} catch {
				// erro já mostrado como pop-up pelo api/client.js
			} finally {
				setCarregando(false);
			}
		}
		carregar();
	}, []);

	return (
		<div>
			<h1>Histórico de Ordens</h1>
			<p className="fechamento-periodo-legenda">
				Todo evento de toda ordem (registro, edição, conclusão), em ordem cronológica.
			</p>

			{carregando ? (
				<LoadingState label="Carregando histórico…" />
			) : eventos.length === 0 ? (
				<EmptyState
					title="Nenhum evento ainda"
					message="Assim que uma ordem for registrada, editada ou completada, aparece aqui."
				/>
			) : (
				<div className="table-card">
					<table>
						<thead>
							<tr>
								<th>Data/hora</th>
								<th>Ordem</th>
								<th>Evento</th>
								<th>Usuário</th>
								<th>Detalhes</th>
							</tr>
						</thead>
						<tbody>
							{eventos.map((evento) => {
								const rotulo = ROTULOS_TIPO[evento.tipo] || { texto: evento.tipo, classe: "badge-neutral" };
								return (
									<tr key={evento.id}>
										<td className="mono">{formatarDataHora(evento.criadoEm)}</td>
										<td className="mono">{evento.idTrade}</td>
										<td>
											<span className={`badge ${rotulo.classe}`}>{rotulo.texto}</span>
										</td>
										<td>{evento.usuarioNome}</td>
										<td>
											{evento.tipo === "EDITADA" ? (
												<>Valores antes da edição: {descreverSnapshot(evento.dadosAnteriores)}</>
											) : (
												descreverSnapshot(evento.dadosAnteriores)
											)}
										</td>
									</tr>
								);
							})}
						</tbody>
					</table>
				</div>
			)}
		</div>
	);
}
