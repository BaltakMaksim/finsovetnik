import { Copy, Check } from 'lucide-react';
import { useState } from 'react';
import styles from './SeedDisplay.module.scss';

interface SeedDisplayProps {
  words: string[];
}

export function SeedDisplay({ words }: SeedDisplayProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    await navigator.clipboard.writeText(words.join(' '));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <span className={styles.icon}>🔑</span>
        <span className={styles.title}>Твоя фраза доступа</span>
      </div>

      <div className={styles.grid}>
        {words.map((word, index) => (
          <div key={index} className={styles.word}>
            <span className={styles.number}>{index + 1}</span>
            <span className={styles.text}>{word}</span>
          </div>
        ))}
      </div>

      <button onClick={handleCopy} className={styles.copyButton}>
        {copied ? (
          <>
            <Check size={16} />
            Скопировано!
          </>
        ) : (
          <>
            <Copy size={16} />
            Скопировать все
          </>
        )}
      </button>

      <div className={styles.warning}>
        ⚠️ Запиши эти слова в надежном месте. Без них ты не сможешь войти!
      </div>
    </div>
  );
}