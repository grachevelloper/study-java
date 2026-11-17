import { useEffect, useId, useRef } from 'react';

export function Dialog({ title, children, onClose }) {
  const ref = useRef(null);
  const heading = useId();

  useEffect(() => {
    const dialog = ref.current;
    dialog.showModal();
    return () => dialog.close();
  }, []);

  return (
    <dialog
      ref={ref}
      aria-labelledby={heading}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
    >
      <header className="dialog-heading">
        <h2 id={heading}>{title}</h2>
        <button type="button" className="quiet close-dialog" aria-label="Закрыть" onClick={onClose}>
          ×
        </button>
      </header>
      <div className="dialog-content">{children}</div>
    </dialog>
  );
}
