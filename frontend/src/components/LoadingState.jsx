import { IconSpinner } from "./icons";

export default function LoadingState({ label = "Carregando…" }) {
	return (
		<div className="loading-state">
			<IconSpinner size={15} strokeWidth="2.5" className="spin" />
			<span>{label}</span>
		</div>
	);
}
