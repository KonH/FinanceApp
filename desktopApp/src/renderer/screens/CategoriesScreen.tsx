import React, { useMemo, useState } from 'react';
import type { Navigator } from '../App';
import { api } from '../api';
import { useStore } from '../store';
import { ConfirmDialog, Dialog, Fab, ListItem, SelectField, TextField, TopBar } from '../components/ui';
import { IconButton } from '../components/Icon';
import { buildPath, flattenWithDepth, toTree } from '../../shared/category';
import type { Category } from '../../shared/types';

interface DialogState {
  id: number | null;
  name: string;
  parentId: number;
}

/** Port of `CategoriesScreen` — the nested CATEGORY_V1 tree. */
export function CategoriesScreen({ nav }: { nav: Navigator }): React.ReactElement {
  const store = useStore();
  const [dialog, setDialog] = useState<DialogState | null>(null);
  const [deleteId, setDeleteId] = useState<number | null>(null);

  const rows = useMemo(
    () => toTree(store.categories).flatMap((root) => flattenWithDepth(root)),
    [store.categories]
  );

  const parentOptions = useMemo(() => {
    const options = store.categories
      .filter((category) => category.id !== dialog?.id)
      .map((category) => ({ value: category.id, label: buildPath(store.categories, category.id) }))
      .sort((a, b) => a.label.localeCompare(b.label));
    return [{ value: -1, label: 'None' }, ...options];
  }, [dialog?.id, store.categories]);

  const save = async (): Promise<void> => {
    if (!dialog) return;
    const category: Category = {
      id: dialog.id ?? 0,
      name: dialog.name.trim(),
      parentId: dialog.parentId
    };
    const okResult = await store.run(() => api.saveCategory(category, dialog.id === null));
    if (okResult) setDialog(null);
  };

  return (
    <div className="screen">
      <TopBar title="Categories" onBack={nav.back} />

      <div className="content">
        {rows.length === 0 && <div className="empty">No categories yet</div>}
        {rows.map(([node, depth]) => (
          <div key={node.category.id} style={{ paddingLeft: depth * 16 }}>
            <ListItem
              title={node.category.name}
              trailing={
                <>
                  <IconButton
                    icon="edit"
                    title="Edit"
                    disabled={store.isReadOnly}
                    onClick={() =>
                      setDialog({
                        id: node.category.id,
                        name: node.category.name,
                        parentId: node.category.parentId
                      })
                    }
                  />
                  <IconButton
                    icon="delete"
                    title="Delete"
                    disabled={store.isReadOnly}
                    onClick={() => setDeleteId(node.category.id)}
                  />
                </>
              }
            />
          </div>
        ))}
      </div>

      {!store.isReadOnly && (
        <Fab title="Add category" onClick={() => setDialog({ id: null, name: '', parentId: -1 })} />
      )}

      {dialog && (
        <Dialog
          title={dialog.id === null ? 'Add category' : 'Edit category'}
          onClose={() => setDialog(null)}
          actions={
            <>
              <button type="button" className="btn" onClick={() => setDialog(null)}>
                Cancel
              </button>
              <button
                type="button"
                className="btn filled"
                disabled={!dialog.name.trim()}
                onClick={() => void save()}
              >
                Save
              </button>
            </>
          }
        >
          <TextField
            label="Name"
            value={dialog.name}
            autoFocus
            onChange={(name) => setDialog({ ...dialog, name })}
          />
          <SelectField
            label="Parent"
            value={dialog.parentId}
            placeholder="None"
            options={parentOptions}
            onChange={(parentId) => setDialog({ ...dialog, parentId: parentId ?? -1 })}
          />
        </Dialog>
      )}

      {deleteId !== null && (
        <ConfirmDialog
          title="Delete category?"
          message="Categories used by transactions or with sub-categories cannot be deleted."
          onCancel={() => setDeleteId(null)}
          onConfirm={() => {
            const id = deleteId;
            setDeleteId(null);
            void store.run(() => api.deleteCategory(id));
          }}
        />
      )}
    </div>
  );
}
