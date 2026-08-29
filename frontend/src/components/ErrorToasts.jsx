import { useEffect, useState } from "react";
import { inscreverErros, consumirErroAgendado } from "../utils/toastBus";
import { IconAlertTriangle } from "./icons";

const DURACAO_MS = 8000;

export default function ErrorToasts() {
	const [toasts, setToasts] = useState([]);

	function remover(id) {
		setToasts((atual) => atual.filter((t) => t.id !== id));
	}

	function adicionar(toast) {
		setToasts((atual) => [...atual, toast]);
		setTimeout(() => remover(toast.id), DURACAO_MS);
	}

	useEffect(() => {
		const mensagemPendente = consumirErroAgendado();
		if (mensagemPendente) {
			adicionar({ id: Date.now(), mensagem: mensagemPendente });
		}
		return inscreverErros(adicionar);
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, []);

	if (toasts.length === 0) return null;

	return (
		<div className="toast-container" role="alert" aria-live="assertive">
			{toasts.map((toast) => (
				<div key={toast.id} className="toast-erro">
					<IconAlertTriangle size={18} />
					<p>{toast.mensagem}</p>
					<button type="button" className="toast-fechar" onClick={() => remover(toast.id)} aria-label="Fechar">
						×
					</button>
				</div>
			))}
		</div>
	);
}
