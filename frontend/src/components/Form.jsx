import { Message, useAction } from './Feedback';

export function Form({ children, onSave, submitText = 'Сохранить', disabled = false }) {
  const action = useAction();

  function submit(event) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    action.run(() => onSave(values));
  }

  return (
    <form onSubmit={submit} aria-busy={action.pending}>
      <Message error>{action.error}</Message>
      {children}
      <div className="actions">
        <button type="submit" className="primary" disabled={disabled || action.pending}>
          {submitText}
        </button>
      </div>
    </form>
  );
}

export function Options({ values }) {
  return Object.entries(values).map(([key, label]) => (
    <option key={key} value={key}>
      {label}
    </option>
  ));
}
