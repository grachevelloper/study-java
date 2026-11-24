import { useEffect, useState } from 'react';
import { api, query } from '../../api/client';
import { useResource } from '../../api/useResource';
import { money, statuses, time } from '../../app/labels';
import { Message, Resource, useAction } from '../../components/Feedback';
import { Pagination, Table } from '../../components/Table';
import { BulkDialog } from './BulkDialog';
import { ImportDialog } from './ImportDialog';
import { NewOrderDialog, OrderDialog, OrderPriority } from './OrderDialog';
import { OrderFilters } from './OrderFilters';

const columns = ['Номер', 'Покупатель', 'Статус', 'Сумма', 'Приоритет', 'Теги', 'Изменён'];

export function OrdersPage({ user, canEdit, isAdmin, customers }) {
    const [filters, setFilters] = useState({});
    const [page, setPage] = useState(0);
    const [selected, setSelected] = useState(() => new Map());
    const [modal, setModal] = useState(null);
    const [feedback, setFeedback] = useState(null);
    const orders = useResource(query('/api/orders', { ...filters, page, size: 15 }));
    const action = useAction();

    useEffect(() => {
        if (orders.data && page > 0 && page >= orders.data.totalPages) {
            setPage(Math.max(0, orders.data.totalPages - 1));
        }
    }, [orders.data, page]);

    function applyFilters(values) {
        setFilters(values);
        setPage(0);
        setSelected(new Map());
        setFeedback(null);
    }

    function select(order, checked) {
        setSelected((current) => {
            const updated = new Map(current);
            if (checked) updated.set(order.id, order.externalId);
            else updated.delete(order.id);
            return updated;
        });
    }

    function close() {
        setModal(null);
    }

    function exportOrders() {
        action.run(async () => {
            const csv = await api(query('/api/orders/export', filters), { format: 'csv' });
            const url = URL.createObjectURL(csv);
            const download = document.createElement('a');
            download.href = url;
            download.download = 'orders.csv';
            download.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        });
    }

    function bulkComplete({ succeeded, errors }) {
        setFeedback({
            error: errors.length > 0,
            text: [
                `Успешно: ${succeeded.length} · Ошибок: ${errors.length}`,
                ...errors.map((item) => `${selected.get(item.id) || item.id} — ${item.message}`),
            ].join('\n'),
        });
        setSelected((current) => {
            const updated = new Map(current);
            succeeded.forEach((id) => updated.delete(id));
            return updated;
        });
        orders.reload();
        close();
    }

    function orderRow(order) {
        const cells = [
            <button type="button" className="link" onClick={() => setModal({ type: 'detail', id: order.id })}>
                {order.externalId}
            </button>,
            order.customerName,
            <span className="badge">
                {statuses[order.status]}
                {order.archived && ' · Архив'}
            </span>,
            money(order.total),
            <OrderPriority order={order} />,
            order.tags.join(', ') || '—',
            time(order.updatedAt),
        ];

        if (canEdit)
            cells.unshift(
                <input
                    type="checkbox"
                    aria-label={`Выбрать заказ ${order.externalId}`}
                    checked={selected.has(order.id)}
                    onChange={(event) => select(order, event.target.checked)}
                />,
            );
        return { id: order.id, cells };
    }

    return (
        <>
            <div className="section-heading">
                <h1>Заказы</h1>
                <div className="actions">
                    <button type="button" disabled={action.pending} onClick={exportOrders}>
                        Экспорт CSV
                    </button>
                    {canEdit && (
                        <>
                            <button type="button" onClick={() => setModal({ type: 'import' })}>
                                Импорт CSV
                            </button>
                            <button
                                type="button"
                                className="primary"
                                disabled={!customers.data}
                                onClick={() => setModal({ type: 'new' })}
                            >
                                Создать заказ
                            </button>
                        </>
                    )}
                </div>
            </div>
            <Message error>{action.error}</Message>
            <Message error={feedback?.error}>{feedback?.text}</Message>
            {customers.error && (
                <>
                    <Message error>{customers.error}</Message>
                    <button type="button" onClick={customers.reload}>
                        Обновить покупателей
                    </button>
                </>
            )}
            <OrderFilters customers={customers.data ?? []} onApply={applyFilters} />
            {canEdit && selected.size > 0 && (
                <div className="selection">
                    <strong>Выбрано: {selected.size}</strong>
                    <button type="button" className="primary" onClick={() => setModal({ type: 'bulk' })}>
                        Массовое действие
                    </button>
                    <button type="button" onClick={() => setSelected(new Map())}>
                        Снять выбор
                    </button>
                </div>
            )}
            <Resource resource={orders}>
                {(data) => (
                    <Table
                        columns={canEdit ? ['Выбор', ...columns] : columns}
                        rows={data.content.map(orderRow)}
                        caption="Заказы"
                        numericColumn="Сумма"
                        pagination={<Pagination data={data} onPage={setPage} />}
                    />
                )}
            </Resource>
            {canEdit && modal?.type === 'new' && (
                <NewOrderDialog
                    customers={customers.data}
                    onClose={close}
                    onSaved={(detail) => {
                        orders.reload();
                        setFeedback({ text: 'Заказ создан.' });
                        setModal({ type: 'detail', id: detail.order.id });
                    }}
                />
            )}
            {modal?.type === 'detail' && (
                <OrderDialog
                    id={modal.id}
                    user={user}
                    canEdit={canEdit}
                    isAdmin={isAdmin}
                    customers={customers.data ?? []}
                    onClose={close}
                    onChange={(removed) => {
                        if (removed) {
                            select({ id: modal.id }, false);
                            setFeedback({ text: 'Заказ удалён.' });
                        }
                        orders.reload();
                    }}
                />
            )}
            {canEdit && modal?.type === 'bulk' && (
                <BulkDialog selected={selected} onClose={close} onApplied={bulkComplete} />
            )}
            {canEdit && modal?.type === 'import' && <ImportDialog onClose={close} onApplied={orders.reload} />}
        </>
    );
}
