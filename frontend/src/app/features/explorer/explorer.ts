import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { combineLatest, map } from 'rxjs';
import { TuiButton, TuiHint, TuiIcon, TuiNotification } from '@taiga-ui/core';
import { TuiSkeleton, TuiTabs } from '@taiga-ui/kit';
import { OrgApi, type ViewMode } from '../../core/api/org-api';
import { errorMessage, type LoadState } from '../../core/load-state';
import { Tree } from '../../shared/tree/tree';
import { departmentItem, employeeItem, findItem, findPath, type TreeItem } from '../../shared/tree/tree-model';
import { Details } from '../details/details/details';

/** Связывает URL, два независимых дерева и выбранную карточку без локальных копий данных API. */
@Component({
  imports: [RouterLink, RouterLinkActive, Tree, Details, TuiButton, TuiHint, TuiIcon, TuiNotification, TuiSkeleton, TuiTabs],
  selector: 'app-explorer',
  styleUrl: './explorer.scss',
  templateUrl: './explorer.html',
})
export class Explorer {
  readonly mode = signal<ViewMode>('departments');
  readonly selectedId = signal<number | null>(null);
  readonly trees = signal<Record<ViewMode, LoadState<TreeItem>>>({
    departments: { status: 'loading' }, employees: { status: 'loading' },
  });
  readonly expansion = signal<Record<ViewMode, ReadonlySet<number>>>({
    departments: new Set(), employees: new Set(),
  });
  readonly state = computed(() => this.trees()[this.mode()]);
  readonly expanded = computed(() => this.expansion()[this.mode()]);
  readonly highlights = computed(() => {
    const tree = this.trees().employees;
    const id = this.selectedId();
    return this.mode() === 'employees' && tree.status === 'success' && id !== null
      ? findItem(tree.data, id)?.dottedManagerIds ?? [] : [];
  });
  readonly label = computed(() => this.mode() === 'departments' ? 'Подразделения' : 'Сотрудники');
  private readonly api = inject(OrgApi);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);

  constructor() {
    const route = inject(ActivatedRoute);
    combineLatest([route.data, route.paramMap]).pipe(takeUntilDestroyed(this.destroy))
      .subscribe(([data, params]) => {
        this.mode.set(data['mode'] === 'employees' ? 'employees' : 'departments');
        const id = params.get('id');
        this.selectedId.set(id === null ? null : Number(id));
        this.revealSelection(this.mode());
      });
    this.load('departments');
    this.load('employees');
  }

  load(mode: ViewMode = this.mode()): void {
    this.setTree(mode, { status: 'loading' });
    const request = mode === 'departments'
      ? this.api.departmentTree().pipe(map(departmentItem))
      : this.api.employeeTree().pipe(map(employeeItem));
    request.pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: data => {
        this.setTree(mode, { status: 'success', data });
        this.expansion.update(all => ({ ...all, [mode]: new Set([data.id, ...data.children.map(child => child.id)]) }));
        this.revealSelection(mode);
      },
      error: error => this.setTree(mode, { status: 'error', message: errorMessage(error) }),
    });
  }

  toggle(id: number): void {
    const next = new Set(this.expanded());
    if (next.has(id)) next.delete(id); else next.add(id);
    this.expansion.update(all => ({ ...all, [this.mode()]: next }));
  }

  select(id: number): void { void this.router.navigate(['/', this.mode(), id]); }
  close(): void { void this.router.navigate(['/', this.mode()]); }

  expandAll(expand: boolean): void {
    const state = this.state();
    if (state.status !== 'success') return;
    const ids = new Set<number>();
    const visit = (item: TreeItem): void => {
      if (item.children.length) ids.add(item.id);
      item.children.forEach(visit);
    };
    if (expand) visit(state.data);
    this.expansion.update(all => ({ ...all, [this.mode()]: ids }));
  }

  private revealSelection(mode: ViewMode): void {
    const state = this.trees()[mode];
    const id = this.selectedId();
    if (this.mode() !== mode || state.status !== 'success' || id === null) return;
    const next = new Set(this.expansion()[mode]);
    const selected = findItem(state.data, id);
    const relatedIds = mode === 'employees' ? selected?.dottedManagerIds ?? [] : [];
    for (const target of [id, ...relatedIds]) {
      findPath(state.data, target).slice(0, -1).forEach(parent => next.add(parent));
    }
    this.expansion.update(all => ({ ...all, [mode]: next }));
  }

  private setTree(mode: ViewMode, state: LoadState<TreeItem>): void {
    this.trees.update(all => ({ ...all, [mode]: state }));
  }
}
