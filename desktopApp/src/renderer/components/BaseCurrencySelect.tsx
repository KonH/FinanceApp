import React from 'react';
import { SelectField } from './ui';
import type { Currency } from '../../shared/types';

/**
 * Port of `BaseCurrencySelector`: the "Base currency" select (with None) plus the
 * exchange-rate loading progress, or the load error with a Retry button.
 */
export function BaseCurrencySelect({
  currencies,
  value,
  progress,
  error,
  onChange,
  onRetry
}: {
  currencies: Currency[];
  value: number | null;
  progress: number | null;
  error: string | null;
  onChange: (currencyId: number | null) => void;
  onRetry: () => void;
}): React.ReactElement {
  return (
    <div>
      <SelectField
        label="Base currency"
        value={value}
        placeholder="None"
        options={currencies.map((c) => ({
          value: c.id,
          label: c.currencySymbol ? `${c.name} (${c.currencySymbol})` : c.name
        }))}
        onChange={onChange}
      />
      {progress !== null && (
        <div style={{ marginTop: 8 }}>
          <div className="progress">
            <span style={{ width: `${progress}%` }} />
          </div>
          <div className="muted small" style={{ marginTop: 4 }}>
            Loading exchange rates… {progress}%
          </div>
        </div>
      )}
      {progress === null && error !== null && (
        <div className="row" style={{ marginTop: 4 }}>
          <span className="small error-text">{error}</span>
          <button type="button" className="btn" onClick={onRetry}>
            Retry
          </button>
        </div>
      )}
    </div>
  );
}
