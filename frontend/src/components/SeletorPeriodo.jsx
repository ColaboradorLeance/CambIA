import { PRESETS_PERIODO } from "../utils/periodos";
import { formatarData } from "../utils/data";

export default function SeletorPeriodo({ titulo, preset, setPreset, inicio, setInicio, fim, setFim }) {
	return (
		<div className="comparativo-seletor-periodo">
			<h4>{titulo}</h4>
			<label>
				Atalho
				<select value={preset} onChange={(e) => setPreset(e.target.value)}>
					{PRESETS_PERIODO.map((p) => (
						<option key={p.valor} value={p.valor}>
							{p.rotulo}
						</option>
					))}
				</select>
			</label>
			{preset === "PERSONALIZADO" ? (
				<>
					<label>
						Data início
						<input type="date" value={inicio} max={fim || undefined} onChange={(e) => setInicio(e.target.value)} />
					</label>
					<label>
						Data fim
						<input type="date" value={fim} min={inicio || undefined} onChange={(e) => setFim(e.target.value)} />
					</label>
				</>
			) : (
				<p className="comparativo-intervalo-resolvido">
					{formatarData(inicio)} a {formatarData(fim)}
				</p>
			)}
		</div>
	);
}
