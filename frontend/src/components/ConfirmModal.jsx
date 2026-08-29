import { useEffect } from "react";

export default function ConfirmModal({
	open,
	title,
	message,
	confirmLabel = "Confirmar",
	cancelLabel = "Cancelar",
	perigo = false,
	onConfirm,
	onCancel,
}) {
	useEffect(() => {
		if (!open) return;
		function aoTeclar(evento) {
			if (evento.key === "Escape") onCancel();
		}
		document.addEventListener("keydown", aoTeclar);
		return () => document.removeEventListener("keydown", aoTeclar);
	}, [open, onCancel]);

	if (!open) return null;

	return (
		<div className="modal-overlay" onClick={onCancel}>
			<div className="modal-card" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
				<h3>{title}</h3>
				<div className="modal-body">{message}</div>
				<div className="modal-footer">
					<button type="button" className="btn btn-secondary" onClick={onCancel}>
						{cancelLabel}
					</button>
					<button type="button" className={`btn ${perigo ? "btn-danger" : "btn-primary"}`} onClick={onConfirm}>
						{confirmLabel}
					</button>
				</div>
			</div>
		</div>
	);
}
