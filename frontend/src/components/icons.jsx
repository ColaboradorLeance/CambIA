function Svg({ children, size = 17, ...props }) {
	return (
		<svg
			width={size}
			height={size}
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			strokeWidth="2"
			strokeLinecap="round"
			strokeLinejoin="round"
			{...props}
		>
			{children}
		</svg>
	);
}

export function IconLogo(props) {
	return (
		<Svg {...props}>
			<path d="M7 7h11l-3-3" />
			<path d="M17 17H6l3 3" />
		</Svg>
	);
}

export function IconGrid(props) {
	return (
		<Svg {...props}>
			<rect x="3" y="3" width="7" height="7" rx="1.5" />
			<rect x="14" y="3" width="7" height="7" rx="1.5" />
			<rect x="3" y="14" width="7" height="7" rx="1.5" />
			<rect x="14" y="14" width="7" height="7" rx="1.5" />
		</Svg>
	);
}

export function IconList(props) {
	return (
		<Svg {...props}>
			<path d="M8 6h13" />
			<path d="M8 12h13" />
			<path d="M8 18h13" />
			<path d="M3 6h.01" />
			<path d="M3 12h.01" />
			<path d="M3 18h.01" />
		</Svg>
	);
}

export function IconCalendarCheck(props) {
	return (
		<Svg {...props}>
			<rect x="3" y="4" width="18" height="17" rx="2" />
			<path d="M3 9h18" />
			<path d="M8 3v4" />
			<path d="M16 3v4" />
			<path d="m9 15 2 2 4-4" />
		</Svg>
	);
}

export function IconBars(props) {
	return (
		<Svg {...props}>
			<path d="M4 19h16" />
			<rect x="6" y="10" width="3" height="6" />
			<rect x="11" y="6" width="3" height="10" />
			<rect x="16" y="13" width="3" height="3" />
		</Svg>
	);
}

export function IconTrend(props) {
	return (
		<Svg {...props}>
			<path d="M3 17l6-6 4 4 8-8" />
			<path d="M15 7h6v6" />
		</Svg>
	);
}

export function IconRanking(props) {
	return (
		<Svg {...props}>
			<path d="M8 21V10" />
			<path d="M14 21V3" />
			<path d="M20 21v-7" />
		</Svg>
	);
}

export function IconClock(props) {
	return (
		<Svg {...props}>
			<circle cx="12" cy="12" r="9" />
			<path d="M12 7v5l3 3" />
		</Svg>
	);
}

export function IconHistory(props) {
	return (
		<Svg {...props}>
			<path d="M3 12a9 9 0 1 0 9-9" />
			<path d="M3 4v8h8" />
		</Svg>
	);
}

export function IconUsers(props) {
	return (
		<Svg {...props}>
			<circle cx="12" cy="8" r="4" />
			<path d="M4 20c0-4 3.5-6 8-6s8 2 8 6" />
		</Svg>
	);
}

export function IconBank(props) {
	return (
		<Svg {...props}>
			<path d="M3 21h18" />
			<path d="M4 21V9l8-5 8 5v12" />
			<path d="M9 21v-6h6v6" />
		</Svg>
	);
}

export function IconTeam(props) {
	return (
		<Svg {...props}>
			<circle cx="9" cy="8" r="3.2" />
			<path d="M2.5 20c0-3.5 3-5.3 6.5-5.3s6.5 1.8 6.5 5.3" />
			<circle cx="17.5" cy="8.5" r="2.3" />
			<path d="M16 14.7c2.7.4 4.5 1.9 4.5 4.4" />
		</Svg>
	);
}

export function IconLogout(props) {
	return (
		<Svg {...props}>
			<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
			<path d="M16 17l5-5-5-5" />
			<path d="M21 12H9" />
		</Svg>
	);
}

export function IconPlus(props) {
	return (
		<Svg {...props}>
			<path d="M12 5v14" />
			<path d="M5 12h14" />
		</Svg>
	);
}

export function IconArrowRight(props) {
	return (
		<Svg {...props}>
			<path d="M5 12h14" />
			<path d="M13 6l6 6-6 6" />
		</Svg>
	);
}

export function IconTrendUp(props) {
	return (
		<Svg {...props}>
			<path d="M5 12l5-5 4 4 5-5" />
			<path d="M14 6h5v5" />
		</Svg>
	);
}

export function IconMoney(props) {
	return (
		<Svg {...props}>
			<path d="M12 2v20" />
			<path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
		</Svg>
	);
}

export function IconVolume(props) {
	return (
		<Svg {...props}>
			<path d="M3 3v18h18" />
			<path d="M7 15l4-6 4 3 5-7" />
		</Svg>
	);
}

export function IconInfo(props) {
	return (
		<Svg {...props}>
			<circle cx="12" cy="12" r="9" />
			<path d="M12 8v5" />
			<path d="M12 16h.01" />
		</Svg>
	);
}

export function IconAlertTriangle(props) {
	return (
		<Svg {...props}>
			<path d="M12 9v4" />
			<path d="M12 17h.01" />
			<path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" />
		</Svg>
	);
}

export function IconInbox(props) {
	return (
		<Svg {...props}>
			<path d="M21 8v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8" />
			<path d="M2 8l3-5h14l3 5" />
			<path d="M2 8h20" />
			<path d="M10 12h4" />
		</Svg>
	);
}

export function IconSpinner(props) {
	return (
		<Svg {...props}>
			<path d="M21 12a9 9 0 1 1-3-6.7" />
		</Svg>
	);
}
