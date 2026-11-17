import { useRef, useState } from 'react';
import { api, query } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Message, useAction } from '../../components/Feedback';
import { Form, Options } from '../../components/Form';

async function upload(operation, form) {
  const mode = form.get('mode');
  const body = new FormData();
  body.set('file', form.get('file'));
  return api(query(`/api/orders/import/${operation}`, { mode }), { method: 'POST', body });
}

export function ImportDialog({ onApplied, onClose }) {
  const [result, setResult] = useState(null);
  const [verified, setVerified] = useState(null);
  const revision = useRef(0);
  const action = useAction();

  function changed() {
    revision.current++;
    setResult(null);
    setVerified(null);
  }

  async function preview(form) {
    const current = revision.current;
    setVerified(null);
    setResult(null);
    const data = await upload('preview', form);
    if (current !== revision.current) return;
    setResult({
      summary: `Корректных строк: ${data.validRows} · С ошибками: ${data.invalidRows}`,
      issues: data.issues,
    });
    if (data.validRows > 0) setVerified(form);
  }

  function apply() {
    if (!verified) return;
    action.run(async () => {
      const current = revision.current;
      const data = await upload('apply', verified);
      onApplied();
      if (current !== revision.current) return;
      setVerified(null);
      setResult({
        summary: `Создано: ${data.created} · Обновлено: ${data.updated} · Пропущено: ${data.skipped}`,
        issues: data.issues,
      });
    });
  }

  return (
    <Dialog title="Импорт CSV" onClose={onClose}>
      <div className="stack">
        <Form submitText="Проверить файл" onSave={preview} disabled={action.pending}>
          <label>
            CSV-файл
            <input name="file" type="file" accept=".csv,text/csv" onChange={changed} required />
          </label>
          <label>
            Режим импорта
            <select name="mode" onChange={changed}>
              <Options
                values={{
                  CREATE_ONLY: 'Только новые заказы',
                  UPSERT_BY_EXTERNAL_ID: 'Добавить или обновить по номеру',
                }}
              />
            </select>
          </label>
          <p className="hint">
            Столбцы: external_id, customer_email, note, urgent, tags, items, status. Позиции —
            SKU:количество через |. Теги — через |.
          </p>
        </Form>
        <Message error>{action.error}</Message>
        {result && (
          <div role="status">
            <strong>{result.summary}</strong>
            {result.issues.length > 0 && (
              <ul>
                {result.issues.map((issue, index) => (
                  <li key={index}>
                    Строка {issue.row} — {issue.message}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
        {verified && (
          <div className="actions">
            <button className="primary" type="button" disabled={action.pending} onClick={apply}>
              Применить импорт
            </button>
          </div>
        )}
      </div>
    </Dialog>
  );
}
