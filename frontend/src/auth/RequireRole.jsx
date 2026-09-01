import { Navigate } from "react-router-dom";
import { useAuth } from "./AuthContext";

export default function RequireRole({ roles, children }) {
	const { usuario } = useAuth();
	if (!roles.includes(usuario?.perfil)) {
		return <Navigate to="/ordens" replace />;
	}
	return children;
}
