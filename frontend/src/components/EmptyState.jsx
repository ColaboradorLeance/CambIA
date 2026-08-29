import { IconInbox } from "./icons";

export default function EmptyState({ title, message, action }) {
	return (
		<div className="empty-state">
			<div className="empty-state-icon">
				<IconInbox size={24} strokeWidth="1.8" />
			</div>
			<h3>{title}</h3>
			{message && <p>{message}</p>}
			{action}
		</div>
	);
}
