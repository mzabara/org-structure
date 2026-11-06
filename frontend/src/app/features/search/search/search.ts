import { Component, DestroyRef, ElementRef, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TuiButton, TuiInput, TuiTextfield } from '@taiga-ui/core';
import { catchError, map, of, Subject, switchMap, timer } from 'rxjs';
import { OrgApi, type SearchResult } from '../../../core/api/org-api';
import { errorMessage, type LoadState } from '../../../core/load-state';
import type { SearchSelection } from '../../../shared/tree/tree-model';

/** Общий поиск отменяет устаревшие запросы и выдаёт путь для раскрытия дерева. */
@Component({
  imports: [FormsModule, TuiButton, TuiInput, TuiTextfield],
  selector: 'app-search',
  styleUrl: './search.scss',
  templateUrl: './search.html',
})
export class Search {
  readonly selected = output<SearchSelection>();
  readonly query = signal('');
  readonly open = signal(false);
  readonly state = signal<LoadState<SearchResult> | null>(null);
  private readonly api = inject(OrgApi);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly requests = new Subject<string>();

  constructor() {
    this.requests.pipe(
      switchMap(q => q.length < 2 ? of(null) : timer(300).pipe(
        switchMap(() => this.api.search(q)),
        map(data => ({ status: 'success', data }) as LoadState<SearchResult>),
        catchError(error => of<LoadState<SearchResult>>({ status: 'error', message: errorMessage(error) })),
      )),
      takeUntilDestroyed(inject(DestroyRef)),
    ).subscribe(state => this.state.set(state));
  }

  update(query: string): void {
    this.query.set(query);
    this.open.set(true);
    this.state.set(query.trim().length >= 2 ? { status: 'loading' } : null);
    this.requests.next(query.trim());
  }

  pick(mode: SearchSelection['mode'], id: number, path: number[]): void {
    this.open.set(false);
    this.selected.emit({ mode, id, path });
  }

  blur(event: FocusEvent): void {
    if (!this.element.nativeElement.contains(event.relatedTarget as Node | null)) this.open.set(false);
  }

  keyboard(event: KeyboardEvent): void {
    if (event.key === 'Escape') { this.open.set(false); return; }
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
    event.preventDefault();
    const items = [...this.element.nativeElement.querySelectorAll<HTMLButtonElement>('.result')];
    const index = items.indexOf(event.target as HTMLButtonElement);
    const next = event.key === 'ArrowDown' ? index + 1 : (index < 0 ? items.length - 1 : index - 1);
    items[(next + items.length) % items.length]?.focus();
  }
}
