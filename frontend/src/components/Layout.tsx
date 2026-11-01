import { NavLink, Outlet } from 'react-router-dom';

const icons = {
  dashboard: 'M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z',
  list: 'M4 6h16M4 12h16M4 18h10',
  calendar:
    'M7 3v3m10-3v3M4 9h16M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z',
  plus: 'M12 5v14M5 12h14',
};

function Icon({ path }: { path: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <path d={path} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

const links = [
  { to: '/', label: 'Dashboard', icon: icons.dashboard, end: true },
  { to: '/subscriptions', label: 'Subscriptions', icon: icons.list, end: true },
  { to: '/renewals', label: 'Renewals', icon: icons.calendar, end: true },
  { to: '/subscriptions/new', label: 'Add', icon: icons.plus, end: true },
];

export function Layout() {
  return (
    <div className="app">
      <aside className="sidebar">
        <NavLink to="/" className="brand">
          <img src="/favicon.svg" alt="" />
          SubSense
        </NavLink>
        <nav className="nav" aria-label="Main">
          {links.map((link) => (
            <NavLink key={link.to} to={link.to} end={link.end}>
              <Icon path={link.icon} />
              {link.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
