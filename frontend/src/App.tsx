// src/App.tsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Toaster } from 'sonner';
import { Sidebar } from '@components/Layout/Sidebar';
import { ChatPage } from '@pages/ChatPage';
import styles from './App.module.scss';

// Заглушки для будущих страниц
function BudgetsPage() {
  return <div className={styles.placeholder}>💰 Раздел «Бюджеты» в разработке</div>;
}

function AnalyticsPage() {
  return <div className={styles.placeholder}>📊 Раздел «Аналитика» в разработке</div>;
}

function SettingsPage() {
  return <div className={styles.placeholder}>⚙️ Раздел «Настройки» в разработке</div>;
}

export default function App() {
  return (
    <BrowserRouter>
      <div className={styles.layout}>
        <Sidebar />
        <main className={styles.content}>
          <Routes>
            <Route path="/" element={<ChatPage />} />
            <Route path="/budgets" element={<BudgetsPage />} />
            <Route path="/analytics" element={<AnalyticsPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Routes>
        </main>
      </div>
      <Toaster position="top-right" richColors closeButton />
    </BrowserRouter>
  );
}