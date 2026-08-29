import { Navigate } from "react-router-dom";
import { useAuth } from "./AuthContext";

export default function RequireAuth({ children }) {
	const { usuario } = useAuth();
	if (!usuario) {
		return <Navigate to="/login" replace />;
	}
	return children;
}
