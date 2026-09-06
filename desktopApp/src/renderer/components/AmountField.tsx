import React, { useState } from 'react';
import { Dialog, Field } from './ui';
import { Icon } from './Icon';

/** Port of `InlineCalculator` — keypad arithmetic that writes back into a field. */
function CalculatorDialog({
  initial,
  onResult,
  onDismiss
}: {
  initial: string;
  onResult: (value: number) => void;
  onDismiss: () => void;
}): React.ReactElement {
  const [display, setDisplay] = useState(() => (Number.isFinite(Number(initial)) && initial ? initial : '0'));
  const [pendingOp, setPendingOp] = useState<string | null>(null);
  const [operand, setOperand] = useState<number | null>(null);
  const [clearNext, setClearNext] = useState(false);

  const apply = (a: number, b: number, op: string | null): number => {
    switch (op) {
      case '+':
        return a + b;
      case '-':
        return a - b;
      case '×':
        return a * b;
      case '÷':
        return b !== 0 ? a / b : a;
      default:
        return b;
    }
  };

  const toDisplay = (value: number): string =>
    Number.isFinite(value) && Math.floor(value) === value ? String(value) : String(Number(value.toFixed(6)));

  const handleDigit = (digit: string): void => {
    if (clearNext) {
      setDisplay(digit);
      setClearNext(false);
      return;
    }
    setDisplay((current) => (current === '0' ? digit : current + digit));
  };

  const handleOp = (op: string): void => {
    const current = Number(display);
    if (!Number.isFinite(current)) return;
    const result = pendingOp !== null && operand !== null ? apply(operand, current, pendingOp) : current;
    setDisplay(toDisplay(result));
    setOperand(result);
    setPendingOp(op);
    setClearNext(true);
  };

  const handleEquals = (): void => {
    const current = Number(display);
    if (!Number.isFinite(current)) return;
    const result = pendingOp !== null && operand !== null ? apply(operand, current, pendingOp) : current;
    onResult(result);
  };

  const keys: Array<{ label: string; onClick: () => void; span?: boolean }> = [
    { label: '7', onClick: () => handleDigit('7') },
    { label: '8', onClick: () => handleDigit('8') },
    { label: '9', onClick: () => handleDigit('9') },
    { label: '÷', onClick: () => handleOp('÷') },
    { label: '4', onClick: () => handleDigit('4') },
    { label: '5', onClick: () => handleDigit('5') },
    { label: '6', onClick: () => handleDigit('6') },
    { label: '×', onClick: () => handleOp('×') },
    { label: '1', onClick: () => handleDigit('1') },
    { label: '2', onClick: () => handleDigit('2') },
    { label: '3', onClick: () => handleDigit('3') },
    { label: '-', onClick: () => handleOp('-') },
    {
      label: 'C',
      onClick: () => {
        setDisplay('0');
        setOperand(null);
        setPendingOp(null);
        setClearNext(false);
      }
    },
    { label: '0', onClick: () => handleDigit('0') },
    {
      label: '.',
      onClick: () => setDisplay((current) => (current.includes('.') ? current : `${current}.`))
    },
    { label: '+', onClick: () => handleOp('+') },
    {
      label: '⌫',
      onClick: () => setDisplay((current) => (current.length <= 1 ? '0' : current.slice(0, -1)))
    },
    { label: '=', onClick: handleEquals, span: true }
  ];

  return (
    <Dialog
      title="Calculator"
      onClose={onDismiss}
      actions={
        <button type="button" className="btn" onClick={onDismiss}>
          Cancel
        </button>
      }
    >
      <div className="calc-display">{display}</div>
      <div className="calc-grid">
        {keys.map((key) => (
          <button
            key={key.label}
            type="button"
            className={key.span ? 'calc-span-2' : undefined}
            onClick={key.onClick}
          >
            {key.label}
          </button>
        ))}
      </div>
    </Dialog>
  );
}

export function AmountField({
  label,
  value,
  onChange,
  autoFocus
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  autoFocus?: boolean;
}): React.ReactElement {
  const [calculatorOpen, setCalculatorOpen] = useState(false);
  return (
    <>
      <Field label={label}>
        <div className="row">
          <input
            className="input grow"
            inputMode="decimal"
            value={value}
            autoFocus={autoFocus}
            onChange={(event) => onChange(event.target.value.replace(',', '.'))}
          />
          <button
            type="button"
            className="icon-btn"
            title="Calculator"
            onClick={() => setCalculatorOpen(true)}
          >
            <Icon name="calculator" />
          </button>
        </div>
      </Field>
      {calculatorOpen && (
        <CalculatorDialog
          initial={value}
          onResult={(result) => {
            onChange(Math.floor(result) === result ? String(result) : String(Number(result.toFixed(2))));
            setCalculatorOpen(false);
          }}
          onDismiss={() => setCalculatorOpen(false)}
        />
      )}
    </>
  );
}
