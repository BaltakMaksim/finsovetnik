import { NavLink} from 'react-router-dom';
import { MessageSquare, PieChart, Settings, Wallet, LogOut } from 'lucide-react';
import { useChatStore } from '@store/useChatStore';
import styles from './Sidebar.module.scss';
import clsx from 'clsx';

const navItems = [
  { to: '/', icon: MessageSquare, label: 'Чат' },
  { to: '/budgets', icon: Wallet, label: 'Бюджеты' },
  { to: '/analytics', icon: PieChart, label: 'Аналитика' },
  { to: '/settings', icon: Settings, label: 'Настройки' },
];

export function Sidebar() {
  const isConnected = useChatStore((state) => state.isConnected);
  const isAuthenticated = useChatStore((state) => state.isAuthenticated);
  const username = useChatStore((state) => state.username);
  const logout = useChatStore((state) => state.logout);

  return (
    <aside className={styles.sidebar}>
      <div className={styles.logo}>
        <Wallet />
        ФинСоветник AI
      </div>

      {/* ✅ Имя пользователя и кнопка выхода */}
      {isAuthenticated && username && (
        <div className={styles.userSection}>
          <div className={styles.userInfo}>
            <div className={styles.userName}>{username}</div>
            <div className={styles.userStatus}>В сети</div>
          </div>
          <button onClick={logout} className={styles.logoutButton} title="Выйти">
            <LogOut size={18} />
          </button>
        </div>
      )}

      <nav className={styles.nav}>
        {navItems.map(({ to, icon: Icon, label }) => {
          // Все вкладки кроме "Чат" неактивны, если не авторизован
          //const isDisabled = !isAuthenticated && to !== '/';
          const isDisabled = true;

          return (
            <NavLink
              key={to}
              to={isDisabled ? '#' : to}
              className={({ isActive: navActive }) =>
                clsx(
                  styles.navItem,
                  navActive && styles.navItemActive,
                  isDisabled && styles.navItemDisabled
                )
              }
              onClick={(e) => {
                if (isDisabled) {
                  e.preventDefault();
                }
              }}
            >
              <Icon />
              {label}
              {isDisabled && <span className={styles.lockIcon}>🔒</span>}
            </NavLink>
          );
        })}
      </nav>

      <div className={styles.status}>
        <span
          className={clsx(
            styles.statusDot,
            isConnected ? styles.connected : styles.disconnected
          )}
        />
        {isConnected ? 'Подключено' : 'Нет соединения'}
      </div>
    </aside>
  );
}