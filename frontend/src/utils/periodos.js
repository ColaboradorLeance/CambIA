function paraIso(data) {
	const ano = data.getFullYear();
	const mes = String(data.getMonth() + 1).padStart(2, "0");
	const dia = String(data.getDate()).padStart(2, "0");
	return `${ano}-${mes}-${dia}`;
}

function intervaloDeMesesAtras(mesesAtras) {
	const hoje = new Date();
	const inicio = new Date(hoje.getFullYear(), hoje.getMonth() - mesesAtras, 1);
	const fim = new Date(hoje.getFullYear(), hoje.getMonth() - mesesAtras + 1, 0);
	return { inicio: paraIso(inicio), fim: paraIso(fim) };
}

function intervaloDeAnosAtras(anosAtras) {
	const ano = new Date().getFullYear() - anosAtras;
	return { inicio: `${ano}-01-01`, fim: `${ano}-12-31` };
}

export const PRESETS_PERIODO = [
	{ valor: "ESTE_MES", rotulo: "Este mês", calcular: () => intervaloDeMesesAtras(0) },
	{ valor: "MES_PASSADO", rotulo: "Mês passado", calcular: () => intervaloDeMesesAtras(1) },
	{ valor: "DOIS_MESES_ATRAS", rotulo: "2 meses atrás", calcular: () => intervaloDeMesesAtras(2) },
	{ valor: "TRES_MESES_ATRAS", rotulo: "3 meses atrás", calcular: () => intervaloDeMesesAtras(3) },
	{ valor: "ESTE_ANO", rotulo: "Este ano", calcular: () => intervaloDeAnosAtras(0) },
	{ valor: "ANO_PASSADO", rotulo: "Ano passado", calcular: () => intervaloDeAnosAtras(1) },
	{ valor: "PERSONALIZADO", rotulo: "Personalizado (escolher datas)", calcular: null },
];
