import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export function Layout() {
  const { logout } = useAuth();
  return (
    <div className="app-shell">
      <header className="app-header">
        <span className="brand">SwiftPay</span>
        <nav>
          <NavLink to="/" end>Hesaplarım</NavLink>
          <NavLink to="/transfers/new">Yeni Transfer</NavLink>
          <NavLink to="/transfers" end>Transferlerim</NavLink>
          <NavLink to="/statistics">İstatistikler</NavLink>
        </nav>
        <button className="link-button" onClick={logout}>Çıkış yap</button>
      </header>
      <main className="app-content">
        <Outlet />
      </main>
    </div>
  );
}
