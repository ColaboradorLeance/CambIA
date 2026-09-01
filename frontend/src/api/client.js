import { mostrarErroGlobal, agendarErroAposRedirecionamento } from "../utils/toastBus";

// "" (vazio, o padrão a partir da Fase 3A da revisão de segurança) significa "mesma
// origem da página" — path relativo, funciona quando front-end e backend estão atrás
// do mesmo reverse-proxy HTTPS. Só é preciso um valor aqui quando NÃO há reverse-proxy
// (backend numa porta própria, endereço diferente da página) — ver README.md.
// Atenção: "??" aqui é proposital, não "||" — string vazia é um valor válido e
// diferente de "não veio nada" (import.meta.env.VITE_API_URL nunca é undefined depois
// do build do Vite, mas o "??" deixa a intenção clara mesmo assim).
const API_URL = import.meta.env.VITE_API_URL ?? "";

// Achado de revisão de segurança (Fase 3B): lê um cookie pelo nome, sem depender de
// nenhuma lib — só usado pro dublê CSRF (XSRF-TOKEN), que é de propósito legível por
// JavaScript (diferente do cookie de sessão em si, esse sim httpOnly).
function lerCookie(nome) {
	const encontrado = document.cookie.split("; ").find((linha) => linha.startsWith(`${nome}=`));
	return encontrado ? decodeURIComponent(encontrado.split("=").slice(1).join("=")) : null;
}

// Achado de revisão de segurança (Fase 3B): quando não há token no localStorage (o caso
// em produção, atrás do reverse-proxy HTTPS — a sessão vive só no cookie httpOnly, nunca
// em localStorage), a requisição depende do cookie de sessão pra autenticar. Nesse caso,
// requisições que mudam estado precisam ecoar o cookie XSRF-TOKEN de volta num header —
// ver CsrfProtectionFilter no backend.
function cabecalhosDeCsrf(metodo, temTokenNoLocalStorage) {
	if (temTokenNoLocalStorage || metodo === "GET") {
		return {};
	}
	const tokenCsrf = lerCookie("XSRF-TOKEN");
	return tokenCsrf ? { "X-XSRF-TOKEN": tokenCsrf } : {};
}

async function request(path, options = {}) {
	const token = localStorage.getItem("sessionToken");
	const metodo = (options.method || "GET").toUpperCase();
	const headers = {
		"Content-Type": "application/json",
		...cabecalhosDeCsrf(metodo, !!token),
		...(options.headers || {}),
	};
	if (token) {
		headers["Authorization"] = `Bearer ${token}`;
	}

	const response = await fetch(`${API_URL}${path}`, { ...options, headers, credentials: "include" });

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

	const response = await fetch(`${API_URL}${path}`, { headers, credentials: "include" });

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
