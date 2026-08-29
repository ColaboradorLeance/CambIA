export function formatarData(data) {
	if (!data) return "—";
	const [ano, mes, dia] = data.slice(0, 10).split("-");
	if (!ano || !mes || !dia) return data;
	return `${dia}/${mes}/${ano}`;
}

export function formatarDataHora(iso) {
	if (!iso) return "—";
	return new Date(iso).toLocaleString("pt-BR");
}
