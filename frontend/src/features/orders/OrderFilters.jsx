import { priorities, statuses } from '../../app/labels';
import { Options } from '../../components/Form';

export function OrderFilters({ customers, onApply }) {
  function submit(event) {
    event.preventDefault();
    const values = Object.fromEntries(new FormData(event.currentTarget));
    if (!values.total) delete values.totalOp;
    onApply(values);
  }

  return (
    <form className="card filters" onSubmit={submit} onReset={() => onApply({})}>
      <label className="search">
        Поиск
        <input
          name="q"
          type="search"
          maxLength={200}
          placeholder="Номер, покупатель, товар или примечание"
        />
      </label>
      <label>
        Список
        <select name="archive">
          <Options values={{ ACTIVE: 'Активные', ARCHIVED: 'Архивные', ALL: 'Все' }} />
        </select>
      </label>
      <label>
        Покупатель
        <select name="customerId">
          <option value="">Все покупатели</option>
          {customers.map((customer) => (
            <option key={customer.id} value={customer.id}>
              {customer.fullName}
            </option>
          ))}
        </select>
      </label>
      <label>
        Статус
        <select name="status">
          <option value="">Все статусы</option>
          <Options values={statuses} />
        </select>
      </label>
      <label>
        Срочность
        <select name="urgent">
          <Options values={{ '': 'Любая', true: 'Срочные', false: 'Обычные' }} />
        </select>
      </label>
      <label>
        Приоритет
        <select name="priority">
          <option value="">Любой</option>
          <Options values={priorities} />
        </select>
      </label>
      <label>
        Тег
        <input name="tag" maxLength={50} placeholder="Тег заказа" />
      </label>
      <div className="amount">
        <label htmlFor="order-filter-total">Сумма, ₽</label>
        <div className="amount-filter">
          <select name="totalOp" aria-label="Сравнение суммы">
            <Options values={{ EQ: '=', GT: '>', GTE: '≥', LT: '<', LTE: '≤' }} />
          </select>
          <input id="order-filter-total" name="total" type="number" min="0" step="0.01" />
        </div>
      </div>
      <label>
        Сортировка
        <select name="sort">
          <Options
            values={{
              'created,desc': 'Сначала новые',
              'created,asc': 'Сначала старые',
              'code,asc': 'Номер заказа',
              'total,desc': 'Сумма по убыванию',
              'total,asc': 'Сумма по возрастанию',
              'status,asc': 'Статус',
            }}
          />
        </select>
      </label>
      <div className="actions filter-actions">
        <button className="primary" type="submit">
          Применить
        </button>
        <button type="reset">Сбросить</button>
      </div>
    </form>
  );
}
