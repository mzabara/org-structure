import { HttpErrorResponse } from '@angular/common/http';

/** Единое состояние запроса не позволяет отображать устаревшие данные как новый результат. */
export type LoadState<T> =
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; message: string };

export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0 || error.status === 502 || error.status === 504) {
      return 'Сервер недоступен. Проверьте подключение и повторите запрос.';
    }
    if (error.status === 404) return 'Запрошенный сотрудник или подразделение не найдено.';
    if (error.status === 401) return 'Сервер требует API-ключ. Для локального запуска выберите профиль dev.';
  }
  return 'Не удалось загрузить данные. Повторите запрос.';
}

export function initials(name: string): string {
  return name.split(/\s+/).slice(0, 2).map(part => part[0] ?? '').join('');
}
