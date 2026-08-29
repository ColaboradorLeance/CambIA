import { mostrarErroGlobal, agendarErroAposRedirecionamento } from "../utils/toastBus";

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

async function request(path, options = {}) {
	const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
	const token = localStorage.getItem("sessionToken");
	if (token) {
		headers["Authorization"] = `Bearer ${token}`;
	}

	const response = await fetch(`${API_URL}${path}`, { ...options, headers });

	if (response.status === 401) {
		const mensagem = "Sessão expirada ou inválida. Faça login novamente.";
		localStorage.removeItem("sessionToken");
		localStorage.removeItem("usuario");
		agendarErroAposRedirecionamento(mensagem);
		window.location.href = "/login";
		throw new Error(mensagem);
	}

	if (!response.ok) {
		const corpo = await response.json().catch(() => ({}));
		// "detail" é o campo do RFC 7807 Problem Details (usado pelo Spring pra erros de
		// validação); "message"/"error" cobrem o formato antigo de erro do Spring Boot.
		const mensagem = corpo.detail || corpo.message || corpo.error || `Erro ${response.status}`;
		mostrarErroGlobal(mensagem);
		throw new Error(mensagem);
	}

	const texto = await response.text();
	return texto ? JSON.parse(texto) : null;
}

async function baixarArquivo(path, nomeArquivo) {
	const headers = {};
	const token = localStorage.getItem("sessionToken");
	if (token) {
		headers["Authorization"] = `Bearer ${token}`;
	}

	const response = await fetch(`${API_URL}${path}`, { headers });

	if (response.status === 401) {
		const mensagem = "Sessão expirada ou inválida. Faça login novamente.";
		localStorage.removeItem("sessionToken");
		localStorage.removeItem("usuario");
		agendarErroAposRedirecionamento(mensagem);
		window.location.href = "/login";
		throw new Error(mensagem);
	}
	if (!response.ok) {
		const corpo = await response.json().catch(() => ({}));
		const mensagem = corpo.detail || corpo.message || corpo.error || `Erro ${response.status} ao baixar o arquivo`;
		mostrarErroGlobal(mensagem);
		throw new Error(mensagem);
	}

	const blob = await response.blob();
	const url = window.URL.createObjectURL(blob);
	const link = document.createElement("a");
	link.href = url;
	link.download = nomeArquivo;
	document.body.appendChild(link);
	link.click();
	link.remove();
	window.URL.revokeObjectURL(url);
}

export const api = {
	get: (path) => request(path),
	post: (path, data) => request(path, { method: "POST", body: JSON.stringify(data) }),
	put: (path, data) => request(path, { method: "PUT", body: JSON.stringify(data) }),
	patch: (path, data) => request(path, { method: "PATCH", body: JSON.stringify(data) }),
	del: (path) => request(path, { method: "DELETE" }),
	baixarArquivo,
};
