import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import { IconLogo, IconArrowRight, IconInfo } from "../components/icons";

export default function LoginPage() {
	const { solicitarLink, verificarToken } = useAuth();
	const navigate = useNavigate();

	const [etapa, setEtapa] = useState("email");
	const [email, setEmail] = useState("");
	const [token, setToken] = useState("");
	const [carregando, setCarregando] = useState(false);

	async function enviarLink(evento) {
		evento.preventDefault();
		setCarregando(true);
		try {
			await solicitarLink(email);
			setEtapa("token");
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		} finally {
			setCarregando(false);
		}
	}

	async function entrar(evento) {
		evento.preventDefault();
		setCarregando(true);
		try {
			await verificarToken(token);
			navigate("/");
		} catch {
			// erro já mostrado como pop-up pelo api/client.js
		} finally {
			setCarregando(false);
		}
	}

	return (
		<div className="login-page">
			<div className="login-page-inner">
				<div className="login-brand">
					<div className="login-brand-mark">
						<IconLogo size={22} stroke="#fff" />
					</div>
					<span className="login-brand-name">CambIA</span>
				</div>

				<div className="login-card">
					{etapa === "email" && (
						<>
							<h1>Entrar na sua conta</h1>
							<p>Controle de ordens de câmbio. Acesso sem senha, por link enviado ao seu e-mail.</p>
							<form onSubmit={enviarLink}>
								<label>
									E-mail
									<input
										type="email"
										placeholder="voce@empresa.com.br"
										value={email}
										onChange={(e) => setEmail(e.target.value)}
										required
									/>
								</label>
								<button type="submit" className="btn btn-primary" disabled={carregando}>
									Enviar link de acesso
									<IconArrowRight size={16} strokeWidth="2.2" />
								</button>
							</form>
						</>
					)}

					{etapa === "token" && (
						<>
							<h1>Verifique seu e-mail</h1>
							<p>
								Se o e-mail estiver cadastrado, um link de acesso foi enviado. Cole abaixo o
								token recebido para entrar.
							</p>
							<form onSubmit={entrar}>
								<label>
									Token
									<input value={token} onChange={(e) => setToken(e.target.value)} required />
								</label>
								<button type="submit" className="btn btn-primary" disabled={carregando}>
									Entrar
								</button>
								<button
									type="button"
									className="btn btn-secondary"
									style={{ marginTop: 8 }}
									onClick={() => setEtapa("email")}
								>
									Voltar
								</button>
							</form>
							<div className="aviso-dev">
								<IconInfo size={16} style={{ flexShrink: 0, marginTop: 1 }} />
								<p style={{ margin: 0 }}>
									Ambiente de desenvolvimento: veja o token de acesso no log do backend (linha
									"Link mágico para...") e cole acima.
								</p>
							</div>
						</>
					)}
				</div>

				<p className="login-footer">Uso interno · CambIA</p>
			</div>
		</div>
	);
}
