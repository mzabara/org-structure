import type { DepartmentTree, EmployeeTree, ViewMode } from '../../core/api/org-api';

/** Модель отображения объединяет подписи двух типов узлов, не дублируя API. */
export interface TreeItem {
  id: number;
  label: string;
  subtitle: string;
  caption: string;
  count?: number;
  dottedManagerIds: number[];
  children: TreeItem[];
}

export function departmentItem(node: DepartmentTree): TreeItem {
  return {
    id: node.id, label: node.name, subtitle: node.code,
    caption: node.head?.fullName ?? 'Руководитель не указан',
    count: node.employeeCount, dottedManagerIds: [], children: node.children.map(departmentItem),
  };
}

export function employeeItem(node: EmployeeTree): TreeItem {
  return {
    id: node.id, label: node.fullName, subtitle: node.position, caption: node.department.name,
    dottedManagerIds: node.dottedManagerIds, children: node.children.map(employeeItem),
  };
}

export function findPath(root: TreeItem, id: number): number[] {
  if (root.id === id) return [id];
  for (const child of root.children) {
    const path = findPath(child, id);
    if (path.length) return [root.id, ...path];
  }
  return [];
}

export function findItem(root: TreeItem, id: number): TreeItem | undefined {
  if (root.id === id) return root;
  for (const child of root.children) {
    const result = findItem(child, id);
    if (result) return result;
  }
  return undefined;
}

/** Путь поиска относится только к дереву выбранного типа. */
export interface SearchSelection { mode: ViewMode; id: number; path: number[]; }
