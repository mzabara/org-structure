import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { TuiIcon, TuiRoot } from '@taiga-ui/core';
import { Search } from './features/search/search/search';
import type { SearchSelection } from './shared/tree/tree-model';

/** Общий каркас сохраняет поиск при переходах между деревьями и карточками. */
@Component({
  imports: [RouterOutlet, RouterLink, TuiRoot, TuiIcon, Search],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);
  select(result: SearchSelection): void {
    void this.router.navigate(['/', result.mode, result.id]);
  }
}
