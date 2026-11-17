import { useState } from 'react';
import { query } from '../../api/client';
import { useResource } from '../../api/useResource';
import { time } from '../../app/labels';
import { Resource } from '../../components/Feedback';
import { Options } from '../../components/Form';
import { Pagination, Table } from '../../components/Table';

export function AuditPage({ active }) {
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState({});
  const audit = useResource(query('/api/admin/audit', { ...filters, page, size: 15 }), active);

  function apply(event) {
    event.preventDefault();
    setFilters(Object.fromEntries(new FormData(event.currentTarget)));
    setPage(0);
  }

  return (
    <>
      <h1>Журнал аудита</h1>
      <form className="card filters" onSubmit={apply}>
        <label>
          Поиск
          <input type="search" name="q" placeholder="Пользователь, событие или ID запроса" />
        </label>
        <label>
          Сортировка
          <select name="sort">
            <Options
              values={{
                'occurred,desc': 'Сначала новые',
                'occurred,asc': 'Сначала старые',
                'actor,asc': 'По пользователю',
                'event,asc': 'По событию',
              }}
            />
          </select>
        </label>
        <button type="submit" className="primary">
          Применить
        </button>
      </form>
      <Resource resource={audit}>
        {(data) => (
          <Table
            columns={['Время', 'Пользователь', 'Событие', 'Описание', 'Запись', 'ID запроса']}
            rows={data.content.map((event) => ({
              id: event.id,
              cells: [
                time(event.occurredAt),
                event.actorEmail,
                event.eventType,
                event.summary,
                event.entityId || event.entityType,
                event.correlationId,
              ],
            }))}
            pagination={<Pagination data={data} onPage={setPage} />}
          />
        )}
      </Resource>
    </>
  );
}
