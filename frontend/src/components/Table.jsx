export function Table({ columns, rows, pagination, caption, numericColumn }) {
  return (
    <div className="card table-card">
      {rows.length > 0 ? (
        <div className="table-scroll">
          <table>
            {caption && <caption className="sr-only">{caption}</caption>}
            <thead>
              <tr>
                {columns.map((column) => (
                  <th key={column} scope="col" className={column === numericColumn ? 'numeric' : undefined}>
                    {column}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map(({ id, cells }) => (
                <tr key={id}>
                  {cells.map((cell, index) => (
                    <td key={columns[index]} className={columns[index] === numericColumn ? 'numeric' : undefined}>
                      {cell ?? '—'}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="empty">Записей нет</p>
      )}
      {pagination}
    </div>
  );
}

export function Pagination({ data, onPage }) {
  return (
    <div className="paging">
      <span>
        {data.totalPages > 0 && `Страница ${data.page + 1} из ${data.totalPages} · `}
        Записей: {data.totalElements}
      </span>
      <div className="actions">
        <button disabled={data.first} type="button" onClick={() => onPage(data.page - 1)}>
          Назад
        </button>
        <button disabled={data.last} type="button" onClick={() => onPage(data.page + 1)}>
          Дальше
        </button>
      </div>
    </div>
  );
}
