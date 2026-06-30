import { useEffect, useRef, useState } from 'react';
import { Html5Qrcode } from 'html5-qrcode';
import { X, Camera, CheckCircle } from 'lucide-react';
import styles from './QRScanner.module.scss';

interface QRScannerProps {
  onSuccess: (qrData: string) => void;
  onClose: () => void;
  onError?: (error: string) => void;
}

export function QRScanner({ onSuccess, onClose, onError }: QRScannerProps) {
  const scannerRef = useRef<Html5Qrcode | null>(null);
  const [status, setStatus] = useState<'idle' | 'scanning' | 'success' | 'error'>('idle');
  const [errorMessage, setErrorMessage] = useState('');
  const isScanningRef = useRef(false); 

  useEffect(() => {
    const scannerId = 'qr-scanner-container';
    const html5QrCode = new Html5Qrcode(scannerId);
    scannerRef.current = html5QrCode;

    const config = {
      fps: 10,
      qrbox: { width: 250, height: 250 },
      aspectRatio: 1.0,
    };

    setStatus('scanning');

    html5QrCode
      .start(
        { facingMode: 'environment' },
        config,
        (decodedText: string) => {
          // ✅ Успешное сканирование
          isScanningRef.current = false; // Помечаем, что сканер остановлен
          setStatus('success');
          
          // Останавливаем сканер
          html5QrCode.stop().catch((err) => {
            console.warn('Ошибка остановки сканера:', err);
          });
          
          // Вызываем callback
          onSuccess(decodedText);
        },
        (scanError) => {
          // Игнорируем ошибки сканирования (нормально, пока не нашёл QR)
          if (!scanError.includes('NotFoundException')) {
            console.warn('QR Scan error:', scanError);
          }
        }
      )
      .then(() => {
        isScanningRef.current = true; // ✅ Сканер запущен
      })
      .catch((err) => {
        console.error('Ошибка запуска камеры:', err);
        setStatus('error');
        setErrorMessage('Не удалось получить доступ к камере. Проверьте разрешения.');
        onError?.('Не удалось получить доступ к камере');
      });

    // ✅ Cleanup при размонтировании
    return () => {
      if (scannerRef.current && isScanningRef.current) {
        scannerRef.current
          .stop()
          .then(() => {
            scannerRef.current?.clear();
          })
          .catch((err) => {
            console.warn('Ошибка очистки сканера:', err);
          });
      }
    };
  }, [onSuccess, onError]);

  const handleClose = () => {
    // ✅ Безопасная остановка сканера
    if (scannerRef.current && isScanningRef.current) {
      scannerRef.current
        .stop()
        .then(() => {
          scannerRef.current?.clear();
          onClose();
        })
        .catch((err) => {
          console.warn('Ошибка остановки сканера:', err);
          onClose(); // Всё равно закрываем
        });
    } else {
      // Сканер уже остановлен
      onClose();
    }
  };

  return (
    <div className={styles.overlay}>
      <div className={styles.container}>
        <div className={styles.header}>
          <h3>Сканирование чека</h3>
          <button className={styles.closeButton} onClick={handleClose}>
            <X size={24} />
          </button>
        </div>

        <div className={styles.scannerArea}>
          <div id="qr-scanner-container"></div>

          {status === 'idle' && (
            <div className={styles.statusMessage}>
              <Camera size={48} />
              <p>Запуск камеры...</p>
            </div>
          )}

          {status === 'error' && (
            <div className={styles.statusMessage}>
              <p>{errorMessage}</p>
              <button onClick={handleClose} className={styles.retryButton}>
                Закрыть
              </button>
            </div>
          )}

          {status === 'success' && (
            <div className={styles.statusMessage}>
              <CheckCircle size={48} color="#4ade80" />
              <p>✅ Чек распознан!</p>
            </div>
          )}
        </div>

        <div className={styles.hint}>
          <p> Наведите камеру на QR-код чека</p>
          <p>QR-код обычно находится внизу чека</p>
        </div>
      </div>
    </div>
  );
}