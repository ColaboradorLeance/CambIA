// Canal simples pra qualquer parte do app (inclusive o api/client.js, que não é um
// componente React) conseguir disparar um pop-up de erro visível na tela.
const ouvintes = new Set();
let proximoId = 1;

const CHAVE_ERRO_PENDENTE = "cambia_erro_pendente";

export function mostrarErroGlobal(mensagem) {
	const toast = { id: proximoId++, mensagem };
	ouvintes.forEach((ouvinte) => ouvinte(toast));
}

export function inscreverErros(ouvinte) {
	ouvintes.add(ouvinte);
	return () => ouvintes.delete(ouvinte);
}

// Usado quando o erro acontece bem antes de um redirecionamento de página inteira (ex.:
// sessão expirada → volta pro /login), que destrói o React e apagaria o pop-up na hora.
// Guarda a mensagem pra ser exibida assim que a página seguinte carregar.
export function agendarErroAposRedirecionamento(mensagem) {
	try {
		sessionStorage.setItem(CHAVE_ERRO_PENDENTE, mensagem);
	} catch {
		// sessionStorage indisponível (modo privado, etc.) — sem problema, só não sobrevive ao reload.
	}
}

export function consumirErroAgendado() {
	try {
		const mensagem = sessionStorage.getItem(CHAVE_ERRO_PENDENTE);
		if (mensagem) {
			sessionStorage.removeItem(CHAVE_ERRO_PENDENTE);
			return mensagem;
		}
	} catch {
		// ignora
	}
	return null;
}
