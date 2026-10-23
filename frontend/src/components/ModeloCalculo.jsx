import { useState } from "react";

/**
 * Nome do modelo de cálculo da ordem; ao passar o mouse (ou focar com o teclado) abre um
 * pop-up com a fórmula da Comissão Líquida (Incremento 87). Posição fixa calculada na hora
 * porque as tabelas rolam na horizontal e cortariam um pop-up absoluto.
 */
const LARGURA_POP = 320;

export default function ModeloCalculo({ nome, formula }) {
	const [posicao, setPosicao] = useState(null);

	if (!nome) {
		return <span className="badge badge-warning">Escolher modelo</span>;
	}

	function abrir(evento) {
		const caixa = evento.currentTarget.getBoundingClientRect();
		// Perto da borda direita, puxa pra esquerda em vez de espremer o texto.
		setPosicao({ top: caixa.bottom + 6, left: Math.max(8, Math.min(caixa.left, window.innerWidth - LARGURA_POP - 8)) });
	}

	return (
		<span
			className="modelo-calculo"
			tabIndex={0}
			onMouseEnter={abrir}
			onFocus={abrir}
			onMouseLeave={() => setPosicao(null)}
			onBlur={() => setPosicao(null)}
		>
			{nome}
			{posicao && formula && (
				<span className="modelo-calculo-pop" role="tooltip" style={posicao}>
					<span className="modelo-calculo-pop-titulo">Comissão Líquida =</span>
					<code>{formula}</code>
					<span className="modelo-calculo-pop-legenda">N = Total Bruto · R = Taxa Rebate</span>
				</span>
			)}
		</span>
	);
}
