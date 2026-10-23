// Rótulos dos escopos (Incremento 81, K4) — usados nas telas de perfil e de Cadastros.
export const ROTULOS_ESCOPO = {
	LEITURA: "Consultar ordens, clientes e bancos",
	CRIAR_ORDENS: "Criar ordens",
};

export const ROTULOS_STATUS_CHAVE = {
	ATIVA: "Ativa",
	REVOGADA: "Revogada",
	EXPIRADA: "Expirada",
};

export function rotulosEscopos(escopos) {
	return (escopos || []).map((escopo) => ROTULOS_ESCOPO[escopo] || escopo).join(" • ");
}
