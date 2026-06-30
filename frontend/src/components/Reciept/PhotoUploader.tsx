import { useRef, useState } from 'react';
import { Upload, X, Loader2, CheckCircle, AlertCircle } from 'lucide-react';
import styles from './PhotoUploader.module.scss';
import { API_URL } from '@/services/auth';

interface PhotoUploaderProps {
  receiptId?: string; // ID чека из QR (для умного слияния)
  onSuccess: (data: any) => void;
  onClose: () => void;
  onError?: (error: string) => void;
}

export function PhotoUploader({ receiptId, onSuccess, onClose, onError }: PhotoUploaderProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [status, setStatus] = useState<'idle' | 'uploading' | 'success' | 'error'>('idle');
  const [preview, setPreview] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // Проверка размера (макс 10 МБ)
    if (file.size > 10 * 1024 * 1024) {
      setErrorMessage('Файл слишком большой (максимум 10 МБ)');
      setStatus('error');
      return;
    }

    setSelectedFile(file);
    
    // Превью
    const reader = new FileReader();
    reader.onload = (e) => setPreview(e.target?.result as string);
    reader.readAsDataURL(file);
    
    setStatus('idle');
  };

  const handleUpload = async () => {
    if (!selectedFile) return;

    setStatus('uploading');

    try {
      const formData = new FormData();
      formData.append('file', selectedFile);
      if (receiptId) {
        formData.append('receipt_id', receiptId); // ✅ Передаём ID для умного слияния
      }

      const response = await fetch(`${API_URL}/api/receipts/scan-photo`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('access_token')}`
        },
        body: formData,
      });

      const data = await response.json();

      if (data.success) {
        setStatus('success');
        setTimeout(() => onSuccess(data.data), 1500); // Даём время увидеть успех
      } else {
        throw new Error(data.error || 'Ошибка обработки');
      }
    } catch (error: any) {
      console.error('Ошибка загрузки фото:', error);
      setStatus('error');
      setErrorMessage(error.message || 'Не удалось загрузить фото');
      onError?.(error.message);
    }
  };

  const handleClose = () => {
    onClose();
  };

  return (
    <div className={styles.overlay}>
      <div className={styles.container}>
        <div className={styles.header}>
          <h3>{receiptId ? '📸 Детализация чека' : '📸 Загрузка чека'}</h3>
          <button className={styles.closeButton} onClick={handleClose}>
            <X size={24} />
          </button>
        </div>

        <div className={styles.content}>
          {receiptId && (
            <div className={styles.hint}>
              <p>
                ✅ QR-код распознан. Теперь загрузи фото чека, чтобы AI определил, 
                на что именно были потрачены деньги.
              </p>
            </div>
          )}

          {/* Превью фото */}
          {preview && (
            <div className={styles.preview}>
              <img src={preview} alt="Превью чека" />
              <button 
                className={styles.removePreview}
                onClick={() => {
                  setPreview(null);
                  setSelectedFile(null);
                  if (fileInputRef.current) fileInputRef.current.value = '';
                }}
              >
                <X size={16} />
              </button>
            </div>
          )}

          {/* Выбор файла */}
          {!preview && (
            <div 
              className={styles.dropZone}
              onClick={() => fileInputRef.current?.click()}
            >
              <Upload size={48} />
              <p>Нажми, чтобы выбрать фото чека</p>
              <span className={styles.hint}>или сфотографируй чек на телефоне</span>
            </div>
          )}

          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            capture="environment" // Задняя камера на мобильных
            onChange={handleFileSelect}
            style={{ display: 'none' }}
          />

          {/* Статус */}
          {status === 'uploading' && (
            <div className={styles.statusMessage}>
              <Loader2 className={styles.spinner} size={32} />
              <p>AI анализирует чек... Это может занять 10-30 секунд</p>
            </div>
          )}

          {status === 'success' && (
            <div className={styles.statusMessage}>
              <CheckCircle size={32} color="#4ade80" />
              <p>✅ Чек успешно распознан!</p>
            </div>
          )}

          {status === 'error' && (
            <div className={styles.statusMessage}>
              <AlertCircle size={32} color="#ef4444" />
              <p>{errorMessage}</p>
            </div>
          )}
        </div>

        {/* Кнопки действий */}
        <div className={styles.actions}>
          <button 
            className={styles.cancelButton}
            onClick={handleClose}
            disabled={status === 'uploading'}
          >
            Отмена
          </button>
          <button 
            className={styles.uploadButton}
            onClick={handleUpload}
            disabled={!selectedFile || status === 'uploading'}
          >
            {status === 'uploading' ? 'Обработка...' : 'Загрузить и проанализировать'}
          </button>
        </div>
      </div>
    </div>
  );
}