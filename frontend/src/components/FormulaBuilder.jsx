import { useEffect, useRef, useState } from "react";
import { api } from "../api/client";

const PALETA = [
	{ tipo: "N", rotulo: "N (Total Bruto)" },
	{ tipo: "R", rotulo: "R (Taxa Rebate)" },
	{ tipo: "paren-open", rotulo: "(" },
	{ tipo: "paren-close", rotulo: ")" },
	{ tipo: "op", valor: "+", rotulo: "+" },
	{ tipo: "op", valor: "-", rotulo: "−" },
	{ tipo: "op", valor: "*", rotulo: "×" },
	{ tipo: "op", valor: "/", rotulo: "÷" },
	{ tipo: "percent", rotulo: "%" },
	{ tipo: "number", valor: "0", rotulo: "nº" },
];

let proximoId = 1;
function novoId() {
	return proximoId++;
}

function rotuloToken(token) {
	switch (token.tipo) {
		case "N":
			return "N";
		case "R":
			return "R";
		case "paren-open":
			return "(";
		case "paren-close":
			return ")";
		case "percent":
			return "%";
		case "op":
			return { "+": "+", "-": "−", "*": "×", "/": "÷" }[token.valor];
		case "number":
			return token.valor;
		default:
			return "?";
	}
}

function tokenParaFormula(token) {
	return token.tipo === "number" ? token.valor : rotuloBruto(token);
}

function rotuloBruto(token) {
	switch (token.tipo) {
		case "N":
			return "N";
		case "R":
			return "R";
		case "paren-open":
			return "(";
		case "paren-close":
			return ")";
		case "percent":
			return "%";
		case "op":
			return token.valor;
		default:
			return "";
	}
}

function tokensParaFormula(tokens) {
	return tokens.map(tokenParaFormula).join("");
}

// Tokenizador simples só para reconstruir os blocos ao editar um banco existente.
function formulaParaTokens(formula) {
	const tokens = [];
	let i = 0;
	const texto = (formula || "").replace(/\s/g, "");
	while (i < texto.length) {
		const c = texto[i];
		if (c === "(") {
			tokens.push({ id: novoId(), tipo: "paren-open" });
			i++;
		} else if (c === ")") {
			tokens.push({ id: novoId(), tipo: "paren-close" });
			i++;
		} else if (c === "%") {
			tokens.push({ id: novoId(), tipo: "percent" });
			i++;
		} else if ("+-*/".includes(c)) {
			tokens.push({ id: novoId(), tipo: "op", valor: c });
			i++;
		} else if (c.toUpperCase() === "N") {
			tokens.push({ id: novoId(), tipo: "N" });
			i++;
		} else if (c.toUpperCase() === "R") {
			tokens.push({ id: novoId(), tipo: "R" });
			i++;
		} else if (/[0-9.,]/.test(c)) {
			let inicio = i;
			while (i < texto.length && /[0-9.,]/.test(texto[i])) i++;
			tokens.push({ id: novoId(), tipo: "number", valor: texto.slice(inicio, i) });
		} else {
			i++;
		}
	}
	return tokens;
}

export default function FormulaBuilder({ value, onChange }) {
	const [tokens, setTokens] = useState(() => formulaParaTokens(value));
	const [totalTeste, setTotalTeste] = useState("1000");
	const [taxaRebateTeste, setTaxaRebateTeste] = useState("0");
	const [resultadoTeste, setResultadoTeste] = useState(null);
	const [erroTeste, setErroTeste] = useState("");
	const ultimoValorExterno = useRef(value);

	useEffect(() => {
		if (value !== ultimoValorExterno.current && value !== tokensParaFormula(tokens)) {
			setTokens(formulaParaTokens(value));
			ultimoValorExterno.current = value;
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [value]);

	function atualizarTokens(novosTokens) {
		setTokens(novosTokens);
		const formula = tokensParaFormula(novosTokens);
		ultimoValorExterno.current = formula;
		onChange(formula);
	}

	function aoSoltarNaPaleta(evento) {
		evento.preventDefault();
		const dados = JSON.parse(evento.dataTransfer.getData("text/plain"));
		if (dados.origem !== "paleta") return;
		const item = PALETA[dados.indice];
		const novoToken = { id: novoId(), tipo: item.tipo, ...(item.valor !== undefined ? { valor: item.valor } : {}) };
		atualizarTokens([...tokens, novoToken]);
	}

	function aoSoltarSobreToken(evento, indiceDestino) {
		evento.preventDefault();
		evento.stopPropagation();
		const dados = JSON.parse(evento.dataTransfer.getData("text/plain"));
		if (dados.origem === "paleta") {
			const item = PALETA[dados.indice];
			const novoToken = { id: novoId(), tipo: item.tipo, ...(item.valor !== undefined ? { valor: item.valor } : {}) };
			const copia = [...tokens];
			copia.splice(indiceDestino, 0, novoToken);
			atualizarTokens(copia);
		} else if (dados.origem === "canvas") {
			const copia = [...tokens];
			const [movido] = copia.splice(dados.indice, 1);
			const destino = dados.indice < indiceDestino ? indiceDestino - 1 : indiceDestino;
			copia.splice(destino, 0, movido);
			atualizarTokens(copia);
		}
	}

	function removerToken(indice) {
		const copia = [...tokens];
		copia.splice(indice, 1);
		atualizarTokens(copia);
	}

	function editarNumero(indice, novoValor) {
		const copia = tokens.map((t, i) => (i === indice ? { ...t, valor: novoValor } : t));
		atualizarTokens(copia);
	}

	async function testarFormula() {
		setErroTeste("");
		setResultadoTeste(null);
		try {
			const resposta = await api.post("/calculos/teste", {
				formula: tokensParaFormula(tokens),
				totalBrutoCambioExemplo: Number(totalTeste),
				taxaRebateExemplo: Number(taxaRebateTeste),
			});
			setResultadoTeste(resposta.resultado);
		} catch (err) {
			setErroTeste(err.message);
		}
	}

	return (
		<div className="formula-builder">
			<div className="formula-paleta">
				{PALETA.map((item, indice) => (
					<span
						key={indice}
						className="formula-chip formula-chip-paleta"
						draggable
						onDragStart={(e) =>
							e.dataTransfer.setData("text/plain", JSON.stringify({ origem: "paleta", indice }))
						}
					>
						{item.rotulo}
					</span>
				))}
			</div>

			<div
				className="formula-canvas"
				onDragOver={(e) => e.preventDefault()}
				onDrop={aoSoltarNaPaleta}
			>
				{tokens.length === 0 && <span className="formula-dica">Arraste blocos aqui para montar a fórmula</span>}
				{tokens.map((token, indice) => (
					<span
						key={token.id}
						className="formula-chip formula-chip-canvas"
						draggable
						onDragStart={(e) =>
							e.dataTransfer.setData("text/plain", JSON.stringify({ origem: "canvas", indice }))
						}
						onDragOver={(e) => e.preventDefault()}
						onDrop={(e) => aoSoltarSobreToken(e, indice)}
					>
						{token.tipo === "number" ? (
							<input
								className="formula-numero-input"
								value={token.valor}
								onChange={(e) => editarNumero(indice, e.target.value)}
								size={Math.max(2, token.valor.length)}
							/>
						) : (
							rotuloToken(token)
						)}
						<button
							type="button"
							className="formula-chip-remover"
							onClick={() => removerToken(indice)}
							aria-label="Remover"
						>
							×
						</button>
					</span>
				))}
			</div>

			<div className="formula-preview">
				Fórmula: <code>{tokensParaFormula(tokens) || "(vazia)"}</code>
			</div>

			<div className="formula-teste">
				<label>
					Testar com Total Bruto de exemplo:
					<input
						type="number"
						value={totalTeste}
						onChange={(e) => setTotalTeste(e.target.value)}
					/>
				</label>
				<label>
					Taxa de Rebate de exemplo:
					<input
						type="number"
						value={taxaRebateTeste}
						onChange={(e) => setTaxaRebateTeste(e.target.value)}
					/>
				</label>
				<button type="button" className="btn btn-secondary btn-sm" onClick={testarFormula}>
					Testar fórmula
				</button>
				{resultadoTeste !== null && <span className="formula-resultado">Resultado: {resultadoTeste}</span>}
				{erroTeste && <span className="erro">{erroTeste}</span>}
			</div>
		</div>
	);
}
