// src/components/Chat/MessageBubble.tsx
import styles from './MessageBabble.module.scss';
import clsx from 'clsx';
import dayjs from 'dayjs';
import { TypeTransaction, type Message} from '@store/useChatStore';

interface Props {
  message: Message;
}
export function MessageBubble({ message }: Props) {
  const isUser = message.sender === 'user';
  console.log(message)
  return (
    <div className={clsx(styles.wrapper, isUser ? styles.user : styles.ai)}>
      <div>
        <div className={styles.bubble}>
          <p>{message.text}</p>

          {message.transactions && message.transactions.length > 0 && (
            <div className={styles.transactions}>
              {message.transactions.map((t, i) => (
                <span key={i} className={ t.type === TypeTransaction.INCOME ? styles.transactionTag: styles.transactionTagExp}>
                   {t.type === TypeTransaction.INCOME ? "+":"-"}{t.amount}₽ · {t.category}
                </span>
              ))}
            </div>
          )}
        </div>

        <div className={styles.timestamp}>
          {dayjs(message.timestamp).format('HH:mm')}
        </div>
      </div>
    </div>
  );
}