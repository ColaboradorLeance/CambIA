const RUS = {
	CONFIRMADO: { texto: "Confirmado", classe: "badge-success" },
	ANDAMENTO: { texto: "Em andamento", classe: "badge-warning" },
	CANCELADO: { texto: "Cancelado", classe: "badge-danger" },
};

export default function StatusBadge({ status }) {
	const config = RUS[status] || { texto: status, classe: "badge-neutral" };
	return <span className={`badge ${config.classe}`}>{config.texto}</span>;
}
