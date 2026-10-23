import { useRef, useState } from "react";
import { formatarData } from "../utils/data";
import { rotulosEscopos } from "../utils/chavesApi";

// Modal próprio (não o ConfirmModal): este NÃO fecha com Esc nem com clique fora — o
// segredo nunca mais é exibido, e um clique acidental faria a pessoa perder a chave.
// Incremento 79 (tela do Admin); desde o Incremento 81, usado na tela "Meu perfil".
export default function ChaveCriadaModal({ chave, onFechar }) {
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
						Pode: <strong>{rotulosEscopos(chave.escopos)}</strong>. Válida até{" "}
						{formatarData(chave.expiraEm)}. Envie no header <span className="mono">X-API-Key</span>.
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
