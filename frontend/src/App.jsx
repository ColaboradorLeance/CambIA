import { Routes, Route, Link, Navigate, useLocation } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import LoginPage from "./auth/LoginPage";
import RequireAuth from "./auth/RequireAuth";
import RequireRole from "./auth/RequireRole";
import ClientesPage from "./pages/ClientesPage";
import BancosPage from "./pages/BancosPage";
import CalculosPage from "./pages/CalculosPage";
import UsuariosPage from "./pages/UsuariosPage";
import OperacoesPage from "./pages/OperacoesPage";
import ListagemOrdensPage from "./pages/ListagemOrdensPage";
import FechamentoPage from "./pages/FechamentoPage";
import HistoricoFechamentosPage from "./pages/HistoricoFechamentosPage";
import RelatorioOperacoesPage from "./pages/RelatorioOperacoesPage";
import RelatorioComparativoPage from "./pages/RelatorioComparativoPage";
import RelatorioRankingsPage from "./pages/RelatorioRankingsPage";
import RelatorioPosicaoAbertoPage from "./pages/RelatorioPosicaoAbertoPage";
import PainelPage from "./pages/PainelPage";
import HistoricoOperacoesPage from "./pages/HistoricoOperacoesPage";
import SubNav from "./components/SubNav";
import ErrorToasts from "./components/ErrorToasts";
import { IconLogo, IconGrid, IconList, IconCalendarCheck, IconTrend, IconUsers, IconLogout } from "./components/icons";
import "./App.css";

const ROTULOS_PERFIL = {
	ADMIN: "Administrador",
	ANALISTA: "Analista",
	CONSULTOR: "Consultor",
};

// Perfis que podem gerenciar/registrar operações, ver relatórios, fechamento e cadastros.
// Consultor fica restrito só a Operações (e seu histórico) — pedido do usuário.
const ADMIN_E_ANALISTA = ["ADMIN", "ANALISTA"];

// Telas antes separadas no menu, agora agrupadas como abas dentro de uma página só
// (Incremento 31) — reduz o número de itens do menu lateral sem remover nenhuma tela.
const ABAS_ORDENS = [
	{ to: "/operacoes", label: "Em andamento", exact: true },
	{ to: "/operacoes/listagem", label: "Confirmadas" },
	{ to: "/operacoes/historico", label: "Histórico" },
];

const ABAS_FECHAMENTO = [
	{ to: "/fechamento", label: "Diário", exact: true },
	{ to: "/fechamento/historico", label: "Histórico" },
];

const ABAS_RELATORIOS = [
	{ to: "/relatorios/operacoes", label: "Ordens" },
	{ to: "/relatorios/comparativo", label: "Comparativos" },
	{ to: "/relatorios/rankings", label: "Rankings" },
	{ to: "/relatorios/posicao-aberto", label: "Posição em aberto" },
];

const ABAS_CADASTROS = [
	{ to: "/cadastros/clientes", label: "Clientes" },
	{ to: "/cadastros/bancos", label: "Bancos" },
	{ to: "/cadastros/calculos", label: "Modelos de Cálculo" },
	{ to: "/cadastros/usuarios", label: "Usuários", roles: ["ADMIN"] },
];

const NAV_GROUPS = [
	{
		titulo: "Operacional",
		itens: [
			{ to: "/", label: "Painel", icon: IconGrid, exact: true, roles: ADMIN_E_ANALISTA },
			{ to: "/operacoes", label: "Ordens", icon: IconList },
			{ to: "/fechamento", label: "Fechamento", icon: IconCalendarCheck, roles: ADMIN_E_ANALISTA },
		],
	},
	{
		titulo: "Relatórios",
		roles: ADMIN_E_ANALISTA,
		itens: [{ to: "/relatorios", label: "Relatórios", icon: IconTrend }],
	},
	{
		titulo: "Cadastros",
		roles: ADMIN_E_ANALISTA,
		itens: [{ to: "/cadastros", label: "Cadastros", icon: IconUsers }],
	},
];

function itemVisivel(item, grupo, perfil) {
	const roles = item.roles || grupo.roles;
	return !roles || roles.includes(perfil);
}

function NavItem({ item }) {
	const location = useLocation();
	const ativo = item.exact ? location.pathname === item.to : location.pathname.startsWith(item.to);
	const Icone = item.icon;
	return (
		<Link to={item.to} className={`navitem${ativo ? " active" : ""}`}>
			<Icone />
			<span>{item.label}</span>
		</Link>
	);
}

function Layout({ children }) {
	const { usuario, logout } = useAuth();
	const inicial = usuario?.nome?.charAt(0)?.toUpperCase() || "?";

	return (
		<div className="app">
			<aside className="app-sidebar">
				<div className="app-sidebar-brand">
					<div className="app-sidebar-brand-mark">
						<IconLogo size={18} stroke="#fff" />
					</div>
					<span className="app-sidebar-brand-name">CambIA</span>
				</div>

				<nav className="app-sidebar-nav">
					{NAV_GROUPS.map((grupo) => {
						const itensVisiveis = grupo.itens.filter((item) => itemVisivel(item, grupo, usuario?.perfil));
						if (itensVisiveis.length === 0) return null;
						return (
							<div key={grupo.titulo}>
								<div className="navsec">{grupo.titulo}</div>
								{itensVisiveis.map((item) => (
									<NavItem key={item.to} item={item} />
								))}
							</div>
						);
					})}
				</nav>

				<div className="app-sidebar-footer">
					<div className="app-user-avatar">{inicial}</div>
					<div className="app-user-info">
						<div className="app-user-name">{usuario?.nome}</div>
						<div className="app-user-role">{ROTULOS_PERFIL[usuario?.perfil] || usuario?.perfil}</div>
					</div>
					<button onClick={logout} aria-label="Sair" title="Sair">
						<IconLogout size={16} />
					</button>
				</div>
			</aside>

			<main className="app-main">{children}</main>
		</div>
	);
}

function Pagina({ roles, children }) {
	const conteudo = <Layout>{children}</Layout>;
	return <RequireAuth>{roles ? <RequireRole roles={roles}>{conteudo}</RequireRole> : conteudo}</RequireAuth>;
}

export default function App() {
	return (
		<>
			<ErrorToasts />
			<Routes>
			<Route path="/login" element={<LoginPage />} />
			<Route
				path="/"
				element={
					<Pagina roles={ADMIN_E_ANALISTA}>
						<PainelPage />
					</Pagina>
				}
			/>
			<Route
				path="/operacoes"
				element={
					<Pagina>
						<SubNav itens={ABAS_ORDENS} />
						<OperacoesPage />
					</Pagina>
				}
			/>
			<Route
				path="/operacoes/listagem"
				element={
					<Pagina>
						<SubNav itens={ABAS_ORDENS} />
						<ListagemOrdensPage />
					</Pagina>
				}
			/>
			<Route
				path="/operacoes/historico"
				element={
					<Pagina>
						<SubNav itens={ABAS_ORDENS} />
						<HistoricoOperacoesPage />
					</Pagina>
				}
			/>
			<Route
				path="/fechamento"
				element={
					<Pagina roles={ADMIN_E_ANALISTA}>
						<SubNav itens={ABAS_FECHAMENTO} />
						<FechamentoPage />
					</Pagina>
				}
			/>
			<Route
				path="/fechamento/historico"
				element={
					<Pagina roles={ADMIN_E_ANALISTA}>
						<SubNav itens={ABAS_FECHAMENTO} />
						<HistoricoFechamentosPage />
					</Pagina>
				}
			/>
			<Route path="/relatorios" element={<Navigate to="/relatorios/operacoes" replace />} />
			<Route
				path="/relatorios/operacoes"
				element={
					<Pagina roles={ADMIN_E_ANALISTA}>
						<SubNav itens={ABAS_RELATORIOS} />
						<RelatorioOperacoesPage />
					</Pagina>
				}
			/>
			<Route
				path="/relatorios/comparativo"
				element={
					<Pagina roles={ADMIN_E_ANALISTA}>
						<SubNav itens={ABAS_RELATORIOS} />
						<RelatorioComparativoPage />
					</Pagina>
				}
			/>
			<Route
				path="/relatorios/rankings"
				element={
					<Pagina roles={ADMIN_E_ANALISTA}>
						<SubNav itens={ABAS_RELATORIOS} />
						<RelatorioRankingsPage />
					</Pagina>
				}
			/>
			<Route
				path="/relatorios/posicao-aberto"
				element={
					<Pagina roles={ADMIN_E_ANALISTA}>
						<SubNav itens={ABAS_RELATORIOS} />
						<RelatorioPosicaoAbertoPage />
					</Pagina>
				}
			/>
			<Route path="/cadastros" element={<Navigate to="/cadastros/clientes" replace />} />
			<Route
				path="/cadastros/clientes"
				element={
					<Pagina roles={ADMIN_E_ANALISTA}>
						<SubNav itens={ABAS_CADASTROS} />
						<ClientesPage />
					</Pagina>
				}
			/>
			<Route
				path="/cadastros/bancos"
				element={
					<Pagina roles={ADMIN_E_ANALISTA}>
						<SubNav itens={ABAS_CADASTROS} />
						<BancosPage />
					</Pagina>
				}
			/>
			<Route
				path="/cadastros/calculos"
				element={
					<Pagina roles={ADMIN_E_ANALISTA}>
						<SubNav itens={ABAS_CADASTROS} />
						<CalculosPage />
					</Pagina>
				}
			/>
			<Route
				path="/cadastros/usuarios"
				element={
					<Pagina roles={["ADMIN"]}>
						<SubNav itens={ABAS_CADASTROS} />
						<UsuariosPage />
					</Pagina>
				}
			/>
		</Routes>
		</>
	);
}
