import { useEffect, useMemo, useRef, useState } from "react";
import { ISO_4217 } from "../data/iso4217";

const LIMITE_RESULTADOS = 40;

function buscar(query) {
	const termo = query.trim().toLowerCase();
	if (!termo) return ISO_4217.slice(0, LIMITE_RESULTADOS);
	const porCodigo = [];
	const porNome = [];
	for (const moeda of ISO_4217) {
		if (moeda.codigo.toLowerCase().startsWith(termo)) {
			porCodigo.push(moeda);
		} else if (moeda.nome.toLowerCase().includes(termo)) {
			porNome.push(moeda);
		}
	}
	return [...porCodigo, ...porNome].slice(0, LIMITE_RESULTADOS);
}

export default function CurrencyPicker({ value, onChange, required }) {
	const [query, setQuery] = useState(value || "");
	const [aberto, setAberto] = useState(false);
	const [indiceAtivo, setIndiceAtivo] = useState(0);
	const raizRef = useRef(null);

	useEffect(() => {
		setQuery(value || "");
	}, [value]);

	useEffect(() => {
		function aoClicarFora(evento) {
			if (raizRef.current && !raizRef.current.contains(evento.target)) {
				setAberto(false);
				setQuery(value || "");
			}
		}
		document.addEventListener("mousedown", aoClicarFora);
		return () => document.removeEventListener("mousedown", aoClicarFora);
	}, [value]);

	const resultados = useMemo(() => buscar(query), [query]);

	function selecionar(moeda) {
		onChange(moeda.codigo);
		setQuery(moeda.codigo);
		setAberto(false);
	}

	function aoTeclar(evento) {
		if (!aberto) return;
		if (evento.key === "ArrowDown") {
			evento.preventDefault();
			setIndiceAtivo((i) => Math.min(i + 1, resultados.length - 1));
		} else if (evento.key === "ArrowUp") {
			evento.preventDefault();
			setIndiceAtivo((i) => Math.max(i - 1, 0));
		} else if (evento.key === "Enter") {
			evento.preventDefault();
			if (resultados[indiceAtivo]) selecionar(resultados[indiceAtivo]);
		} else if (evento.key === "Escape") {
			setAberto(false);
			setQuery(value || "");
		}
	}

	return (
		<div className="currency-picker" ref={raizRef}>
			<input
				value={query}
				placeholder="Buscar moeda (ISO 4217)"
				required={required}
				onFocus={() => {
					setAberto(true);
					setIndiceAtivo(0);
				}}
				onChange={(e) => {
					setQuery(e.target.value);
					setAberto(true);
					setIndiceAtivo(0);
				}}
				onKeyDown={aoTeclar}
				onBlur={() => {
					setAberto(false);
					setQuery(value || "");
				}}
				autoComplete="off"
			/>
			{aberto && (
				<div className="currency-picker-dropdown">
					{resultados.length === 0 ? (
						<div className="currency-picker-vazio">Nenhuma moeda ISO 4217 encontrada</div>
					) : (
						resultados.map((moeda, indice) => (
							<div
								key={moeda.codigo}
								className={`currency-picker-item${indice === indiceAtivo ? " ativo" : ""}${
									moeda.codigo === value ? " selecionado" : ""
								}`}
								onMouseDown={(e) => e.preventDefault()}
								onMouseEnter={() => setIndiceAtivo(indice)}
								onClick={() => selecionar(moeda)}
							>
								<span className="currency-picker-codigo">{moeda.codigo}</span>
								<span className="currency-picker-nome">{moeda.nome}</span>
							</div>
						))
					)}
				</div>
			)}
		</div>
	);
}
