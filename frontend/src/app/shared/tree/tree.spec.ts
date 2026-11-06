import { TestBed } from '@angular/core/testing';
import { Tree } from './tree';
import { findPath, type TreeItem } from './tree-model';

const leaf: TreeItem = { id: 12, label: 'Белова Дарья Михайловна', subtitle: 'Разработчик',
  caption: 'Frontend-разработка', dottedManagerIds: [21], children: [] };
const root: TreeItem = { id: 1, label: 'Орлов Сергей Викторович', subtitle: 'Генеральный директор',
  caption: 'Генеральная дирекция', dottedManagerIds: [], children: [leaf] };

describe('Tree', () => {
  beforeEach(() => {
    HTMLElement.prototype.scrollIntoView = vi.fn();
    TestBed.configureTestingModule({ imports: [Tree] });
  });

  it('строит путь только в своём дереве', () => {
    expect(findPath(root, 12)).toEqual([1, 12]);
    expect(findPath(root, 999)).toEqual([]);
  });

  it('скрывает свёрнутую ветку и раскрывает её по входному сигналу', async () => {
    const fixture = TestBed.createComponent(Tree);
    fixture.componentRef.setInput('root', root);
    fixture.componentRef.setInput('expanded', new Set());
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('[role="treeitem"]').length).toBe(1);
    fixture.componentRef.setInput('expanded', new Set([1]));
    await fixture.whenStable();
    expect(element.querySelectorAll('[role="treeitem"]').length).toBe(2);
  });

  it('поддерживает раскрытие стрелкой и выбор Enter', async () => {
    const fixture = TestBed.createComponent(Tree);
    fixture.componentRef.setInput('root', root);
    fixture.componentRef.setInput('expanded', new Set());
    const toggled = vi.fn();
    const picked = vi.fn();
    fixture.componentInstance.toggled.subscribe(toggled);
    fixture.componentInstance.picked.subscribe(picked);
    await fixture.whenStable();
    const row = (fixture.nativeElement as HTMLElement).querySelector('[role="treeitem"]')!;
    row.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    expect(toggled).toHaveBeenCalledWith(1);
    row.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(picked).toHaveBeenCalledWith(1);
  });

  it('выделяет матричного руководителя отдельно от выбранного сотрудника', async () => {
    const fixture = TestBed.createComponent(Tree);
    fixture.componentRef.setInput('root', root);
    fixture.componentRef.setInput('expanded', new Set([1]));
    fixture.componentRef.setInput('selectedId', 12);
    fixture.componentRef.setInput('highlighted', [1]);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-node="12"] > .node')?.classList.contains('selected')).toBe(true);
    expect(element.querySelector('[data-node="1"] > .node')?.classList.contains('matrix')).toBe(true);
  });
});
