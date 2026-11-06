import { NgTemplateOutlet } from '@angular/common';
import { afterRenderEffect, Component, ElementRef, inject, input, output } from '@angular/core';
import { TuiButton, TuiHint, TuiIcon } from '@taiga-ui/core';
import { TuiAvatar, TuiBadge } from '@taiga-ui/kit';
import { initials } from '../../core/load-state';
import type { TreeItem } from './tree-model';

/** Рекурсивное дерево обеспечивает выбор, независимое раскрытие и навигацию стрелками. */
@Component({
  imports: [NgTemplateOutlet, TuiButton, TuiIcon, TuiHint, TuiAvatar, TuiBadge],
  selector: 'app-tree',
  styleUrl: './tree.scss',
  templateUrl: './tree.html',
})
export class Tree {
  readonly root = input.required<TreeItem>();
  readonly expanded = input.required<ReadonlySet<number>>();
  readonly selectedId = input<number | null>(null);
  readonly highlighted = input<readonly number[]>([]);
  readonly people = input(false);
  readonly toggled = output<number>();
  readonly picked = output<number>();
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  readonly initials = initials;

  constructor() {
    afterRenderEffect(() => {
      const selected = this.selectedId();
      this.expanded();
      if (selected !== null) {
        this.element.nativeElement.querySelector<HTMLElement>(`[data-node="${selected}"]`)
          ?.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'instant' });
      }
    });
  }

  keyboard(event: KeyboardEvent, node: TreeItem): void {
    if ((event.target as HTMLElement).closest('button')) return;
    const rows = [...this.element.nativeElement.querySelectorAll<HTMLElement>('[role="treeitem"]')];
    const current = event.currentTarget as HTMLElement;
    const index = rows.indexOf(current);
    let target: HTMLElement | undefined;
    switch (event.key) {
      case 'ArrowDown': target = rows[index + 1]; break;
      case 'ArrowUp': target = rows[index - 1]; break;
      case 'Home': target = rows[0]; break;
      case 'End': target = rows.at(-1); break;
      case 'ArrowRight':
        if (node.children.length && !this.expanded().has(node.id)) this.toggled.emit(node.id);
        else if (node.children.length) target = rows[index + 1];
        break;
      case 'ArrowLeft':
        if (node.children.length && this.expanded().has(node.id)) this.toggled.emit(node.id);
        else target = current.parentElement?.parentElement?.closest<HTMLElement>('[role="treeitem"]') ?? undefined;
        break;
      case 'Enter':
      case ' ': this.picked.emit(node.id); break;
      default: return;
    }
    event.preventDefault();
    event.stopPropagation();
    target?.focus();
  }
}
