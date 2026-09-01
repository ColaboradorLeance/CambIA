// Extraído de OperacoesPage/ListagemOrdensPage (Incremento 61) — as duas telas mostram os
// mesmos campos calculados (pedido do usuário: nenhuma diferença de coluna entre "Em
// andamento" e "Confirmadas"), então precisam formatar os valores exatamente do mesmo
// jeito. Antes cada tela tinha sua própria cópia de formatarMoeda, já divergentes em
// potencial — centralizado aqui pra nunca mais desalinhar.

export function formatarMoeda(valor) {
	if (valor === null || valor === undefined) return "—";
	return Number(valor).toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 6 });
}

// Spread liquidação/Custo vêm do backend como razão decimal (ex: 0.020) — a exibição em %
// é só formatação de tela, o valor armazenado/retornado pela API continua sendo a razão.
export function formatarPercentual(razao) {
	if (razao === null || razao === undefined) return "—";
	return `${(Number(razao) * 100).toLocaleString("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 3 })}%`;
}

// Spread emissão é texto livre (Incremento 56): "NA" literal quando o tipo da ordem é
// Pronto, ou uma razão decimal em formato de texto (ex: "0.020") nos outros casos — não dá
// pra tratar como número sempre, ao contrário dos outros campos de spread.
export function formatarSpreadEmissao(valor) {
	if (valor === null || valor === undefined) return "—";
	if (valor.toUpperCase() === "NA") return "NA";
	return formatarPercentual(valor);
}
