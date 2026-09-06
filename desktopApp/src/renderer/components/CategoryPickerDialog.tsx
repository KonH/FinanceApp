import React, { useMemo, useState } from 'react';
import { Dialog } from './ui';
import { Icon } from './Icon';
import { buildPath, findNode, toTree } from '../../shared/category';
import type { Category, CategoryNode } from '../../shared/types';

/** Port of `CategoryPickerDialog`: collapsed tree, or flat full paths while searching. */
export function CategoryPickerDialog({
  categories,
  onSelect,
  onDismiss
}: {
  categories: Category[];
  onSelect: (category: Category) => void;
  onDismiss: () => void;
}): React.ReactElement {
  const roots = useMemo(() => toTree(categories), [categories]);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [query, setQuery] = useState('');
  const searching = query.trim().length > 0;

  const rows: Array<[CategoryNode, number]> = useMemo(() => {
    if (!searching) return roots.flatMap((root) => flattenExpanded(root, expanded, 0));
    const q = query.trim().toLowerCase();
    return categories
      .filter((cat) => cat.name.toLowerCase().includes(q))
      .map((cat) => findNode(roots, cat.id))
      .filter((node): node is CategoryNode => node !== null)
      .map((node) => [node, 0] as [CategoryNode, number]);
  }, [categories, expanded, query, roots, searching]);

  const toggle = (id: number): void => {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  return (
    <Dialog
      title="Select category"
      onClose={onDismiss}
      actions={
        <button type="button" className="btn" onClick={onDismiss}>
          Cancel
        </button>
      }
    >
      <input
        className="input"
        placeholder="Search"
        value={query}
        autoFocus
        onChange={(event) => setQuery(event.target.value)}
      />
      <div style={{ maxHeight: '46vh', overflowY: 'auto' }}>
        {rows.length === 0 && <div className="empty">No categories</div>}
        {rows.map(([node, depth]) => {
          const hasChildren = node.children.length > 0 && !searching;
          const label = searching ? buildPath(categories, node.category.id) : node.category.name;
          return (
            <div key={node.category.id} className="row" style={{ paddingLeft: depth * 16 }}>
              {hasChildren ? (
                <button
                  type="button"
                  className="icon-btn"
                  title={expanded.has(node.category.id) ? 'Collapse' : 'Expand'}
                  onClick={() => toggle(node.category.id)}
                >
                  <Icon name={expanded.has(node.category.id) ? 'chevron-down' : 'chevron-right'} />
                </button>
              ) : (
                <span style={{ width: 34, flex: '0 0 34px' }} />
              )}
              <button type="button" className="tree-row grow" onClick={() => onSelect(node.category)}>
                {label}
              </button>
            </div>
          );
        })}
      </div>
    </Dialog>
  );
}

function flattenExpanded(
  node: CategoryNode,
  expanded: Set<number>,
  depth: number
): Array<[CategoryNode, number]> {
  const rows: Array<[CategoryNode, number]> = [[node, depth]];
  if (expanded.has(node.category.id)) {
    for (const child of node.children) rows.push(...flattenExpanded(child, expanded, depth + 1));
  }
  return rows;
}
