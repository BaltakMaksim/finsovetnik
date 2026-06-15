// src/utils/cn.ts
import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Объединяет CSS-классы с поддержкой условной логики и разрешением конфликтов Tailwind.
 * 
 * @example
 * cn('px-4 py-2', isActive && 'bg-primary', className)
 *
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}