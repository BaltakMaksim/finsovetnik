import { useEffect, useState } from 'react';
import { TrendingUp, TrendingDown, Wallet, Receipt, Calendar } from 'lucide-react';
import styles from './Dashboard.module.scss';
import { useChatStore } from '@store/useChatStore';
import { API_URL } from '@/services/auth';

interface Summary {
  totalIncome: number;
  totalExpense: number;
  balance: number;
  totalTransactions: number;
  byCategory: Record<string, number>;
  period: string;
}

interface Transaction {
  id: number;
  amount: number;
  category: string;
  type: 'INCOME' | 'EXPENSE';
  reply: string;
  createdAt: number;
}

export function Dashboard() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [period, setPeriod] = useState<'month' | 'year' | 'all'>('month');
  const [loading, setLoading] = useState(true);

  const isAuthenticated = useChatStore((s) => s.isAuthenticated);

  useEffect(() => {
    if (!isAuthenticated) return;
    loadData();
  }, [period, isAuthenticated]);

  const loadData = async () => {
    setLoading(true);
    const token = localStorage.getItem('access_token');
    const headers = { 'Authorization': `Bearer ${token}` };

    try {
      const [summaryRes, transRes] = await Promise.all([
        fetch(`${API_URL}/api/dashboard/summary?period=${period}`, { headers }),
        fetch(`${API_URL}/api/dashboard/transactions?period=${period}`, { headers }),
      ]);

      if (summaryRes.ok) setSummary(await summaryRes.json());
      if (transRes.ok) {
        const data = await transRes.json();
        setTransactions(data.transactions || []);
      }
    } catch (err) {
      console.error('Ошибка загрузки dashboard:', err);
    } finally {
      setLoading(false);
    }
  };

  const formatMoney = (n: number) =>
    new Intl.NumberFormat('ru-RU', {
      style: 'currency',
      currency: 'RUB',
      maximumFractionDigits: 0,
    }).format(n);

  const formatDate = (ts: number) =>
    new Date(ts).toLocaleDateString('ru-RU', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });

  // Эмодзи для категорий
  const getCategoryEmoji = (cat: string): string => {
    const map: Record<string, string> = {
      'Продукты': '🛒',
      'Кафе и рестораны': '☕',
      'Транспорт': '🚗',
      'Аптека': '💊',
      'Одежда': '👕',
      'Развлечения': '🎮',
      'Дом': '🏠',
      'Связь': '📱',
      'Услуги': '💇',
      'Зарплата': '💰',
      'Другое': '📦',
    };
    return map[cat] || '📦';
  };

  if (loading) {
    return (
      <div className={styles.container}>
        <div className={styles.loading}>
          <div className={styles.spinner}></div>
          <p>Загружаем вашу финансовую сводку...</p>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      {/* Заголовок */}
      <div className={styles.header}>
        <div>
          <h1>Личный кабинет</h1>
          <p className={styles.subtitle}>Ваша финансовая сводка</p>
        </div>
        <div className={styles.periodFilter}>
          <Calendar size={16} />
          <select value={period} onChange={(e) => setPeriod(e.target.value as any)}>
            <option value="month">Последний месяц</option>
            <option value="year">Последний год</option>
            <option value="all">Всё время</option>
          </select>
        </div>
      </div>

      {/* Карточки сводки */}
      {summary && (
        <div className={styles.summaryGrid}>
          <div className={`${styles.card} ${styles.cardIncome}`}>
            <div className={styles.cardIcon}>
              <TrendingUp size={28} />
            </div>
            <div className={styles.cardContent}>
              <div className={styles.cardLabel}>Доходы</div>
              <div className={styles.cardValue}>{formatMoney(summary.totalIncome)}</div>
            </div>
          </div>

          <div className={`${styles.card} ${styles.cardExpense}`}>
            <div className={styles.cardIcon}>
              <TrendingDown size={28} />
            </div>
            <div className={styles.cardContent}>
              <div className={styles.cardLabel}>Расходы</div>
              <div className={styles.cardValue}>{formatMoney(summary.totalExpense)}</div>
            </div>
          </div>

          <div className={`${styles.card} ${styles.cardBalance}`}>
            <div className={styles.cardIcon}>
              <Wallet size={28} />
            </div>
            <div className={styles.cardContent}>
              <div className={styles.cardLabel}>Баланс</div>
              <div className={styles.cardValue}>{formatMoney(summary.balance)}</div>
            </div>
          </div>

          <div className={`${styles.card} ${styles.cardCount}`}>
            <div className={styles.cardIcon}>
              <Receipt size={28} />
            </div>
            <div className={styles.cardContent}>
              <div className={styles.cardLabel}>Операций</div>
              <div className={styles.cardValue}>{summary.totalTransactions}</div>
            </div>
          </div>
        </div>
      )}

      {/* Расходы по категориям */}
      {summary && Object.keys(summary.byCategory).length > 0 && (
        <div className={styles.section}>
          <div className={styles.sectionHeader}>
            <h2>📊 Расходы по категориям</h2>
            <span className={styles.sectionHint}>за период</span>
          </div>
          <div className={styles.categoryList}>
            {Object.entries(summary.byCategory)
              .sort((a, b) => b[1] - a[1])
              .map(([cat, amount]) => {
                const percent =
                  summary.totalExpense > 0 ? (amount / summary.totalExpense) * 100 : 0;
                return (
                  <div key={cat} className={styles.categoryItem}>
                    <div className={styles.categoryHeader}>
                      <div className={styles.categoryLeft}>
                        <span className={styles.categoryEmoji}>{getCategoryEmoji(cat)}</span>
                        <span className={styles.categoryName}>{cat}</span>
                      </div>
                      <div className={styles.categoryRight}>
                        <span className={styles.categoryAmount}>{formatMoney(amount)}</span>
                        <span className={styles.categoryPercent}>{percent.toFixed(0)}%</span>
                      </div>
                    </div>
                    <div className={styles.progressBar}>
                      <div
                        className={styles.progressFill}
                        style={{ width: `${percent}%` }}
                      />
                    </div>
                  </div>
                );
              })}
          </div>
        </div>
      )}

      {/* История операций */}
      <div className={styles.section}>
        <div className={styles.sectionHeader}>
          <h2>📋 История операций</h2>
          <span className={styles.sectionHint}>
            {transactions.length} {transactions.length === 1 ? 'операция' : 'операций'}
          </span>
        </div>

        {transactions.length === 0 ? (
          <div className={styles.empty}>
            <div className={styles.emptyIcon}>💸</div>
            <p>За этот период операций нет</p>
            <p className={styles.emptyHint}>
              Начните общаться с AI-ассистентом, чтобы фиксировать расходы
            </p>
          </div>
        ) : (
          <div className={styles.transactionList}>
            {transactions.map((t) => (
              <div
                key={t.id}
                className={`${styles.transaction} ${
                  t.type === 'INCOME' ? styles.transactionIncome : styles.transactionExpense
                }`}
              >
                <div className={styles.transactionEmoji}>
                  {t.type === 'INCOME' ? '📈' : getCategoryEmoji(t.category)}
                </div>
                <div className={styles.transactionInfo}>
                  <div className={styles.transactionCategory}>{t.category}</div>
                  <div className={styles.transactionDesc}>{t.reply || '—'}</div>
                  <div className={styles.transactionDate}>{formatDate(t.createdAt)}</div>
                </div>
                <div
                  className={`${styles.transactionAmount} ${
                    t.type === 'INCOME' ? styles.amountIncome : styles.amountExpense
                  }`}
                >
                  {t.type === 'INCOME' ? '+' : '−'}
                  {formatMoney(t.amount)}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}