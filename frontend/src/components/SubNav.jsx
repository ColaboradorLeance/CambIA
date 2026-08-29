import { Link, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export default function SubNav({ itens }) {
	const location = useLocation();
	const { usuario } = useAuth();
	const visiveis = itens.filter((item) => !item.roles || item.roles.includes(usuario?.perfil));

	return (
		<div className="subnav">
			{visiveis.map((item) => {
				const ativo = item.exact ? location.pathname === item.to : location.pathname.startsWith(item.to);
				return (
					<Link key={item.to} to={item.to} className={`subnav-tab${ativo ? " active" : ""}`}>
						{item.label}
					</Link>
				);
			})}
		</div>
	);
}
