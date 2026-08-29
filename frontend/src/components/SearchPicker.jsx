import { useEffect, useMemo, useRef, useState } from "react";

const LIMITE_RESULTADOS = 40;

function buscar(itens, query) {
	const termo = query.trim().toLowerCase();
	if (!termo) return itens.slice(0, LIMITE_RESULTADOS);
	const porInicio = [];
	const porTrecho = [];
	for (const item of itens) {
		const label = item.label.toLowerCase();
		if (label.startsWith(termo)) {
			porInicio.push(item);
		} else if (label.includes(termo)) {
			porTrecho.push(item);
		}
	}
	return [...porInicio, ...porTrecho].slice(0, LIMITE_RESULTADOS);
}

function rotuloSelecionado(itens, value) {
	return itens.find((item) => String(item.id) === String(value))?.label || "";
}

export default function SearchPicker({ items, value, onChange, placeholder, required }) {
	const [query, setQuery] = useState(() => rotuloSelecionado(items, value));
	const [aberto, setAberto] = useState(false);
	const [indiceAtivo, setIndiceAtivo] = useState(0);
	const raizRef = useRef(null);

	useEffect(() => {
		setQuery(rotuloSelecionado(items, value));
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [value, items]);

	useEffect(() => {
		function aoClicarFora(evento) {
			if (raizRef.current && !raizRef.current.contains(evento.target)) {
				setAberto(false);
				setQuery(rotuloSelecionado(items, value));
			}
		}
		document.addEventListener("mousedown", aoClicarFora);
		return () => document.removeEventListener("mousedown", aoClicarFora);
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [value, items]);

	const resultados = useMemo(() => buscar(items, query), [items, query]);

	function selecionar(item) {
		onChange(item.id);
		setQuery(item.label);
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
			setQuery(rotuloSelecionado(items, value));
		}
	}

	return (
		<div className="search-picker" ref={raizRef}>
			<input
				value={query}
				placeholder={placeholder}
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
					setQuery(rotuloSelecionado(items, value));
				}}
				autoComplete="off"
			/>
			{aberto && (
				<div className="search-picker-dropdown">
					{resultados.length === 0 ? (
						<div className="search-picker-vazio">Nenhum resultado encontrado</div>
					) : (
						resultados.map((item, indice) => (
							<div
								key={item.id}
								className={`search-picker-item${indice === indiceAtivo ? " ativo" : ""}${
									String(item.id) === String(value) ? " selecionado" : ""
								}`}
								onMouseDown={(e) => e.preventDefault()}
								onMouseEnter={() => setIndiceAtivo(indice)}
								onClick={() => selecionar(item)}
							>
								{item.label}
							</div>
						))
					)}
				</div>
			)}
		</div>
	);
}
