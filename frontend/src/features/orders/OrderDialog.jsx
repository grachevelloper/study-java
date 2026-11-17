import { useState } from 'react';
import { api } from '../../api/client';
import { useResource } from '../../api/useResource';
import { money, priorities, statuses, time } from '../../app/labels';
import { Dialog } from '../../components/Dialog';
import { Message, Resource, useAction } from '../../components/Feedback';
import { Form } from '../../components/Form';
import { Table } from '../../components/Table';
import { OrderForm } from './OrderForm';

function Notes({ path, editable, user, isAdmin }) {
  const notes = useResource(path);
  const action = useAction();
  const [editing, setEditing] = useState(null);

  async function save(data) {
    const id = editing.id;
    await api(id ? `${path}/${id}` : path, {
      method: id ? 'PUT' : 'POST',
      body: { body: data.get('body'), pinned: data.has('pinned'), version: editing.version },
    });
    setEditing(null);
    notes.reload();
  }

  function remove(note) {
    if (window.confirm('Удалить заметку?')) {
      action.run(async () => {
        await api(`${path}/${note.id}`, { method: 'DELETE' });
        notes.reload();
      });
    }
  }

  return (
    <section className="stack">
      <div className="section-heading note-heading">
        <h3>Заметки</h3>
        {editable && !editing && (
          <button type="button" onClick={() => setEditing({})}>
            Добавить заметку
          </button>
        )}
      </div>
      <Message error>{action.error}</Message>
      {editing && (
        <div className="card note">
          <Form key={editing.id ?? 'new'} onSave={save}>
            <label>
              Текст
              <textarea name="body" defaultValue={editing.body ?? ''} required maxLength={4000} />
            </label>
            <label className="inline">
              Закрепить
              <input name="pinned" type="checkbox" defaultChecked={editing.pinned ?? false} />
            </label>
            <button type="button" className="cancel-edit" onClick={() => setEditing(null)}>
              Отмена
            </button>
          </Form>
        </div>
      )}
      <Resource resource={notes}>
        {(items) =>
          items.length ? (
            items.map((note) => (
              <article key={note.id} className="card note">
                <div className="hint">
                  {note.authorName} · {time(note.createdAt)}
                  {note.pinned ? ' · Закреплена' : ''}
                </div>
                <p>{note.body}</p>
                {editable && (isAdmin || note.authorId === user.id) && (
                  <div className="actions">
                    <button type="button" onClick={() => setEditing(note)}>
                      Изменить
                    </button>
                    <button
                      type="button"
                      className="danger"
                      disabled={action.pending}
                      onClick={() => remove(note)}
                    >
                      Удалить
                    </button>
                  </div>
                )}
              </article>
            ))
          ) : (
            <p className="hint">Пока нет заметок.</p>
          )
        }
      </Resource>
    </section>
  );
}

export function OrderPriority({ order }) {
  return <span className={order.urgent ? 'badge urgent' : 'badge'}>{priorities[order.priority]}</span>;
}

export function NewOrderDialog({ customers, onSaved, onClose }) {
  return (
    <Dialog title="Новый заказ" onClose={onClose}>
      <OrderForm customers={customers} onSaved={onSaved} />
    </Dialog>
  );
}

export function OrderDialog({ id, user, canEdit, isAdmin, customers, onChange, onClose }) {
  const path = `/api/orders/${id}`;
  const detail = useResource(path);
  const action = useAction();
  const [editing, setEditing] = useState(false);
  const [notice, setNotice] = useState('');
  const order = detail.data?.order;

  function archive() {
    action.run(async () => {
      if (order.archived) await api(`${path}/restore`, { method: 'POST' });
      else await api(path, { method: 'DELETE' });
      setNotice(order.archived ? 'Заказ восстановлен.' : 'Заказ отправлен в архив.');
      detail.reload();
      onChange();
    });
  }

  function remove() {
    if (window.confirm(`Окончательно удалить заказ ${order.externalId}?`)) {
      action.run(async () => {
        await api(`${path}/permanent`, { method: 'DELETE' });
        onChange(true);
        onClose();
      });
    }
  }

  const title = order ? `${editing ? 'Редактирование заказа' : 'Заказ'} ${order.externalId}` : 'Заказ';
  return (
    <Dialog title={title} onClose={onClose}>
      <Resource resource={detail}>
        {(data) =>
          editing ? (
            <OrderForm
              detail={data}
              customers={customers}
              onCancel={() => setEditing(false)}
              onSaved={() => {
                setNotice('Заказ изменён.');
                setEditing(false);
                detail.reload();
                onChange();
              }}
            />
          ) : (
            <div className="stack">
              <Message error>{action.error}</Message>
              <Message>{notice}</Message>
              <div className="actions">
                {order.customerName}
                <span className="badge">{statuses[order.status]}</span>
                <OrderPriority order={order} />
                {order.archived && <span className="badge">В архиве</span>}
                <strong>{money(order.total)}</strong>
              </div>
              <p className="hint">
                Создан {time(order.createdAt)} · Изменён {time(order.updatedAt)}
              </p>
              {order.note && <div>{order.note}</div>}
              {order.tags.length > 0 && <div>Теги: {order.tags.join(', ')}</div>}
              <Table
                caption="Позиции заказа"
                columns={['Товар', 'Кол-во', 'Цена']}
                rows={data.items.map((item) => ({
                  id: item.productId,
                  cells: [item.name, item.quantity, money(item.unitPrice)],
                }))}
              />
              <div className="actions">
                {canEdit && !order.archived && (
                  <button type="button" className="primary" onClick={() => setEditing(true)}>
                    Редактировать
                  </button>
                )}
                {canEdit && (
                  <button type="button" disabled={action.pending || detail.loading} onClick={archive}>
                    {order.archived ? 'Восстановить' : 'В архив'}
                  </button>
                )}
                {isAdmin && order.archived && ['NEW', 'CANCELLED'].includes(order.status) && (
                  <button type="button" className="danger" disabled={action.pending} onClick={remove}>
                    Удалить окончательно
                  </button>
                )}
              </div>
              <Notes
                path={`${path}/notes`}
                user={user}
                isAdmin={isAdmin}
                editable={canEdit && !order.archived}
              />
            </div>
          )
        }
      </Resource>
    </Dialog>
  );
}
