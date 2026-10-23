import { useState } from "react";
import { api } from "../api/client";

// Incremento 82: "Baixar PDF" / "Baixar Excel" de qualquer relatório. Recebe a mesma query
// que a tela usou pra carregar os dados — o arquivo sai exatamente com o que está na tela
// (mesmo período, mesmos filtros). O backend responde em /<caminho>/pdf ou /<caminho>/xlsx.
export default function BotoesExportacao({ caminho, query = "", nomeArquivo, desabilitado = false }) {
	const [gerando, setGerando] = useState(null);

	async function baixar(formato) {
		setGerando(formato);
		try {
			const sufixo = query ? `?${query}` : "";
			await api.baixarArquivo(`${caminho}/${formato}${sufixo}`, `${nomeArquivo}.${formato}`);
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		} finally {
			setGerando(null);
		}
	}

	return (
		<div className="fechamento-downloads">
			<button
				type="button"
				className="btn btn-secondary"
				disabled={desabilitado || gerando !== null}
				onClick={() => baixar("pdf")}
			>
				{gerando === "pdf" ? "Gerando PDF…" : "Baixar PDF"}
			</button>
			<button
				type="button"
				className="btn btn-secondary"
				disabled={desabilitado || gerando !== null}
				onClick={() => baixar("xlsx")}
			>
				{gerando === "xlsx" ? "Gerando Excel…" : "Baixar Excel"}
			</button>
		</div>
	);
}
