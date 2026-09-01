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

// Achado de negócio (Incremento 62): Fundo é derivado direto do tipo da ordem (PR/CR/VIR)
// — "P" quando é câmbio pronto, "M" pros outros tipos (Crédito/Virtual). Não depende de
// nenhum dado novo nem precisa vir da API — é só uma tradução do prCrVir que a Operação já
// tem, calculada aqui pra aparecer igual nas duas telas (Em andamento e Confirmadas), do
// mesmo jeito que Custo (Incremento 57) e Spread emissão (Incremento 56) já usam esse
// mesmo campo pra decidir o próprio valor.
export function calcularFundo(prCrVir) {
	if (prCrVir === null || prCrVir === undefined) return "—";
	return prCrVir.toLowerCase() === "pronto" ? "P" : "M";
}
