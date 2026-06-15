// src/components/Layout/Sidebar.tsx
import { NavLink } from 'react-router-dom';
import { MessageSquare, PieChart, Settings, Wallet } from 'lucide-react';
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

  return (
    <aside className={styles.sidebar}>
      <div className={styles.logo}>
        <Wallet />
        ФинСоветник
      </div>

      <nav className={styles.nav}>
        {navItems.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              clsx(styles.navItem, isActive && styles.navItemActive)
            }
          >
            <Icon />
            {label}
          </NavLink>
        ))}
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