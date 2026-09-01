import { createContext, useContext, useState } from "react";
import { api } from "../api/client";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
	const [usuario, setUsuario] = useState(() => {
		const armazenado = localStorage.getItem("usuario");
		return armazenado ? JSON.parse(armazenado) : null;
	});

	async function solicitarLink(email) {
		await api.post("/auth/magic-link", { email });
	}

	async function verificarToken(token) {
		const dados = await api.get(`/auth/verify?token=${encodeURIComponent(token)}`);
		localStorage.setItem("sessionToken", dados.sessionToken);
		localStorage.setItem("usuario", JSON.stringify(dados.usuario));
		setUsuario(dados.usuario);
	}

	// Achado de revisão de segurança: antes só limpava o navegador — a sessão continuava
	// válida no servidor até expirar (8h) mesmo depois do "logout". Agora avisa o backend
	// pra invalidar de verdade (DELETE /auth/sessao), antes de limpar o localStorage (a
	// chamada precisa do token ainda presente pra identificar qual sessão encerrar).
	async function logout() {
		try {
			await api.del("/auth/sessao");
		} catch {
			// mesmo se a chamada falhar (ex.: backend fora do ar), ainda limpa localmente
		}
		localStorage.removeItem("sessionToken");
		localStorage.removeItem("usuario");
		setUsuario(null);
	}

	return (
		<AuthContext.Provider value={{ usuario, solicitarLink, verificarToken, logout }}>
			{children}
		</AuthContext.Provider>
	);
}

export function useAuth() {
	return useContext(AuthContext);
}
