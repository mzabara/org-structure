import { Component, computed, DestroyRef, inject, input, OnChanges, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TuiButton, TuiLink, TuiNotification } from '@taiga-ui/core';
import { TuiAvatar, TuiBadge, TuiSkeleton, TuiSwitch } from '@taiga-ui/kit';
import { Observable, Subscription } from 'rxjs';
import { OrgApi, type Department, type Employee, type EmployeePage, type ViewMode } from '../../../core/api/org-api';
import { errorMessage, initials, type LoadState } from '../../../core/load-state';

/** Панель связанных карточек загружает реальные данные и отдельную страницу сотрудников отдела. */
@Component({
  imports: [RouterLink, FormsModule, TuiButton, TuiLink, TuiNotification, TuiAvatar, TuiBadge, TuiSkeleton, TuiSwitch],
  selector: 'app-details',
  styleUrl: './details.scss',
  templateUrl: './details.html',
})
export class Details implements OnChanges {
  readonly mode = input.required<ViewMode>();
  readonly entityId = input.required<number>();
  readonly closed = output<void>();
  readonly state = signal<LoadState<Department | Employee>>({ status: 'loading' });
  readonly staff = signal<LoadState<EmployeePage>>({ status: 'loading' });
  readonly includeSubdepartments = signal(false);
  readonly page = signal(0);
  readonly initials = initials;
  readonly employee = computed(() => {
    const state = this.state();
    return state.status === 'success' && 'email' in state.data ? state.data : null;
  });
  readonly department = computed(() => {
    const state = this.state();
    return state.status === 'success' && 'code' in state.data ? state.data : null;
  });
  readonly tasks = computed(() => this.employee()?.assignments.filter(item => item.kind === 'TASK') ?? []);
  readonly responsibilities = computed(() => this.employee()?.assignments.filter(item => item.kind === 'RESPONSIBILITY') ?? []);
  private readonly api = inject(OrgApi);
  private readonly destroy = inject(DestroyRef);
  private cardRequest?: Subscription;
  private staffRequest?: Subscription;

  ngOnChanges(): void {
    this.includeSubdepartments.set(false);
    this.page.set(0);
    this.load();
  }

  load(): void {
    this.cardRequest?.unsubscribe();
    this.state.set({ status: 'loading' });
    const request: Observable<Department | Employee> = this.mode() === 'employees'
      ? this.api.employee(this.entityId()) : this.api.department(this.entityId());
    this.cardRequest = request.pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: data => this.state.set({ status: 'success', data }),
      error: error => this.state.set({ status: 'error', message: errorMessage(error) }),
    });
    if (this.mode() === 'departments') this.loadStaff();
  }

  loadStaff(): void {
    this.staffRequest?.unsubscribe();
    this.staff.set({ status: 'loading' });
    this.staffRequest = this.api.departmentEmployees(this.entityId(), this.includeSubdepartments(), this.page())
      .pipe(takeUntilDestroyed(this.destroy)).subscribe({
        next: data => this.staff.set({ status: 'success', data }),
        error: error => this.staff.set({ status: 'error', message: errorMessage(error) }),
      });
  }

  toggleStaff(include: boolean): void {
    this.includeSubdepartments.set(include);
    this.page.set(0);
    this.loadStaff();
  }

  changePage(delta: number): void {
    this.page.update(page => page + delta);
    this.loadStaff();
  }
}
