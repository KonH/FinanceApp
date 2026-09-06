import React, { useEffect } from 'react';
import { Icon, IconButton, type IconName } from './Icon';

export function TopBar({
  title,
  subtitle,
  onBack,
  actions
}: {
  title: string;
  subtitle?: string;
  onBack?: () => void;
  actions?: React.ReactNode;
}): React.ReactElement {
  return (
    <header className="topbar">
      {onBack && <IconButton icon="back" title="Back" onClick={onBack} />}
      <h1 className="topbar-title">
        {title}
        {subtitle !== undefined && (
          <>
            <br />
            <span className="topbar-subtitle">{subtitle}</span>
          </>
        )}
      </h1>
      <div className="topbar-actions">{actions}</div>
    </header>
  );
}

export function Dialog({
  title,
  children,
  actions,
  onClose,
  dismissible = true
}: {
  title: string;
  children: React.ReactNode;
  actions: React.ReactNode;
  onClose?: () => void;
  dismissible?: boolean;
}): React.ReactElement {
  useEffect(() => {
    const onKey = (event: KeyboardEvent): void => {
      if (event.key === 'Escape' && dismissible && onClose) onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [dismissible, onClose]);

  return (
    <div
      className="scrim"
      onMouseDown={(event) => {
        if (dismissible && onClose && event.target === event.currentTarget) onClose();
      }}
    >
      <div className="dialog" role="dialog" aria-modal="true" aria-label={title}>
        <h2>{title}</h2>
        <div className="dialog-body">{children}</div>
        <div className="dialog-actions">{actions}</div>
      </div>
    </div>
  );
}

export function ConfirmDialog({
  title,
  message,
  confirmLabel = 'Delete',
  danger = true,
  onConfirm,
  onCancel
}: {
  title: string;
  message: string;
  confirmLabel?: string;
  danger?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}): React.ReactElement {
  return (
    <Dialog
      title={title}
      onClose={onCancel}
      actions={
        <>
          <button type="button" className="btn" onClick={onCancel}>
            Cancel
          </button>
          <button type="button" className={`btn ${danger ? 'danger' : 'filled'}`} onClick={onConfirm}>
            {confirmLabel}
          </button>
        </>
      }
    >
      <p style={{ margin: 0 }}>{message}</p>
    </Dialog>
  );
}

export function Field({
  label,
  children
}: {
  label: string;
  children: React.ReactNode;
}): React.ReactElement {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
    </label>
  );
}

export function TextField({
  label,
  value,
  onChange,
  placeholder,
  type = 'text',
  autoFocus,
  multiline
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  type?: string;
  autoFocus?: boolean;
  multiline?: boolean;
}): React.ReactElement {
  return (
    <Field label={label}>
      {multiline ? (
        <textarea
          className="input"
          value={value}
          placeholder={placeholder}
          onChange={(event) => onChange(event.target.value)}
        />
      ) : (
        <input
          className="input"
          type={type}
          value={value}
          placeholder={placeholder}
          autoFocus={autoFocus}
          onChange={(event) => onChange(event.target.value)}
        />
      )}
    </Field>
  );
}

export interface Option<T> {
  value: T;
  label: string;
}

export function SelectField<T extends string | number>({
  label,
  value,
  options,
  onChange,
  placeholder = 'Select…'
}: {
  label: string;
  value: T | null;
  options: Array<Option<T>>;
  onChange: (value: T | null) => void;
  placeholder?: string;
}): React.ReactElement {
  return (
    <Field label={label}>
      <select
        className="select"
        value={value === null || value === undefined ? '' : String(value)}
        onChange={(event) => {
          const raw = event.target.value;
          if (raw === '') {
            onChange(null);
            return;
          }
          const match = options.find((option) => String(option.value) === raw);
          onChange(match ? match.value : null);
        }}
      >
        <option value="">{placeholder}</option>
        {options.map((option) => (
          <option key={String(option.value)} value={String(option.value)}>
            {option.label}
          </option>
        ))}
      </select>
    </Field>
  );
}

export function Segmented<T extends string>({
  value,
  options,
  onChange
}: {
  value: T;
  options: Array<Option<T>>;
  onChange: (value: T) => void;
}): React.ReactElement {
  return (
    <div className="segmented" role="radiogroup">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={value === option.value}
          className={value === option.value ? 'selected' : ''}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

export function ListItem({
  title,
  subtitle,
  trailing,
  onClick,
  leading
}: {
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  trailing?: React.ReactNode;
  onClick?: () => void;
  leading?: React.ReactNode;
}): React.ReactElement {
  const content = (
    <>
      {leading}
      <div className="list-item-main">
        <div className="list-item-title">{title}</div>
        {subtitle !== undefined && <div className="list-item-sub">{subtitle}</div>}
      </div>
      {trailing !== undefined && <div className="list-item-trailing">{trailing}</div>}
    </>
  );
  if (onClick) {
    return (
      <div
        className="list-item clickable"
        role="button"
        tabIndex={0}
        onClick={onClick}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onClick();
          }
        }}
      >
        {content}
      </div>
    );
  }
  return <div className="list-item">{content}</div>;
}

export function Fab({ title, onClick }: { title: string; onClick: () => void }): React.ReactElement {
  return (
    <button type="button" className="fab" title={title} aria-label={title} onClick={onClick}>
      <Icon name="add" size={24} />
    </button>
  );
}

export function ActionIcon(props: {
  icon: IconName;
  title: string;
  onClick: () => void;
  disabled?: boolean;
  className?: string;
}): React.ReactElement {
  return <IconButton {...props} />;
}
