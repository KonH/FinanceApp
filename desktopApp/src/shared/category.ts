import type { Category, CategoryNode } from './types';

/** Builds the parent/child forest of categories (roots have PARENTID = -1). */
export function toTree(categories: Category[]): CategoryNode[] {
  const byParent = new Map<number, Category[]>();
  for (const cat of categories) {
    const list = byParent.get(cat.parentId);
    if (list) list.push(cat);
    else byParent.set(cat.parentId, [cat]);
  }
  const build = (cat: Category): CategoryNode => ({
    category: cat,
    children: (byParent.get(cat.id) ?? []).map(build)
  });
  return (byParent.get(-1) ?? []).map(build);
}

export function flattenWithDepth(node: CategoryNode, depth = 0): Array<[CategoryNode, number]> {
  return [[node, depth] as [CategoryNode, number]].concat(
    node.children.flatMap((child) => flattenWithDepth(child, depth + 1))
  );
}

/** Full `Parent/Child/Grandchild` path of a category id. */
export function buildPath(categories: Category[], id: number | null | undefined): string {
  if (id === null || id === undefined) return '';
  const byId = new Map(categories.map((c) => [c.id, c]));
  const parts: string[] = [];
  let cur = byId.get(id);
  const guard = new Set<number>();
  while (cur && !guard.has(cur.id)) {
    guard.add(cur.id);
    parts.unshift(cur.name);
    cur = cur.parentId === -1 ? undefined : byId.get(cur.parentId);
  }
  return parts.join('/');
}

export function findNode(nodes: CategoryNode[], id: number): CategoryNode | null {
  for (const node of nodes) {
    if (node.category.id === id) return node;
    const found = findNode(node.children, id);
    if (found) return found;
  }
  return null;
}

/** Ids of a category and everything below it — used to guard re-parenting. */
export function descendantIds(categories: Category[], id: number): Set<number> {
  const result = new Set<number>([id]);
  let changed = true;
  while (changed) {
    changed = false;
    for (const cat of categories) {
      if (!result.has(cat.id) && result.has(cat.parentId)) {
        result.add(cat.id);
        changed = true;
      }
    }
  }
  return result;
}
