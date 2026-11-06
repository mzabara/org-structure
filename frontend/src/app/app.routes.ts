import { Routes } from '@angular/router';
import { Explorer } from './features/explorer/explorer';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'departments' },
  { path: 'departments', component: Explorer, data: { mode: 'departments' }, title: 'Подразделения | Оргструктура' },
  { path: 'departments/:id', component: Explorer, data: { mode: 'departments' }, title: 'Подразделение | Оргструктура' },
  { path: 'employees', component: Explorer, data: { mode: 'employees' }, title: 'Сотрудники | Оргструктура' },
  { path: 'employees/:id', component: Explorer, data: { mode: 'employees' }, title: 'Сотрудник | Оргструктура' },
  { path: '**', redirectTo: 'departments' },
];
