import { useRef, useState } from 'react';
import { api } from '../../api/client';
import { useResource } from '../../api/useResource';
import { money, statuses } from '../../app/labels';
import { Resource } from '../../components/Feedback';
import { Form, Options } from '../../components/Form';

const nextStatuses = {
    NEW: ['PAID', 'CANCELLED'],
    PAID: ['SHIPPED', 'CANCELLED'],
    SHIPPED: ['DELIVERED'],
};
const orderCodePattern = '[A-Za-z0-9][A-Za-z0-9._\\-]{0,99}';

export function OrderForm({ detail, customers, onSaved, onCancel }) {
    const order = detail?.order;
    const editable = !order || order.status === 'NEW';
    const products = useResource('/api/products');
    const [positions, setPositions] = useState(() =>
        (detail?.items ?? [{ productId: '', quantity: 1 }]).map((item, key) => ({
            key,
            productId: item.productId,
            quantity: item.quantity,
        })),
    );
    const nextKey = useRef(positions.length);
    const choices = order ? [order.status, ...(nextStatuses[order.status] ?? [])] : ['NEW'];

    function editPosition(key, field, value) {
        setPositions((current) =>
            current.map((item) => (item.key === key ? { ...item, [field]: value } : item)),
        );
    }

    function addPosition() {
        const key = nextKey.current++;
        setPositions((current) => [...current, { key, productId: '', quantity: 1 }]);
    }

    async function save(data) {
        const body = {
            externalId: editable ? data.get('externalId') : order.externalId,
            customerId: editable ? Number(data.get('customerId')) : order.customerId,
            note: (editable ? data.get('note') : order.note) ?? '',
            urgent: editable ? data.has('urgent') : order.urgent,
            tags: data
                .get('tags')
                .split(',')
                .map((tag) => tag.trim())
                .filter(Boolean),
            status: data.get('status'),
            version: order?.version,
            items: positions.map((item) => ({
                productId: Number(item.productId),
                quantity: Number(item.quantity),
            })),
        };
        const detail = await api(order ? `/api/orders/${order.id}` : '/api/orders', {
            method: order ? 'PUT' : 'POST',
            body,
        });
        onSaved(detail);
    }

    return (
        <Resource resource={products}>
            {(catalog) => (
                <Form onSave={save}>
                    <div className="form-grid">
                        <label>
                            Номер заказа
                            <input
                                name="externalId"
                                defaultValue={order?.externalId}
                                pattern={orderCodePattern}
                                maxLength={100}
                                disabled={!editable}
                                required
                            />
                        </label>
                        <label>
                            Покупатель
                            <select
                                name="customerId"
                                defaultValue={order?.customerId ?? ''}
                                required
                                disabled={!editable}
                            >
                                <option value="">Выберите покупателя</option>
                                {customers
                                    .filter((customer) => customer.active || customer.id === order?.customerId)
                                    .map((customer) => (
                                        <option key={customer.id} value={customer.id}>
                                            {customer.fullName}
                                            {customer.active ? '' : ' · отключён'}
                                        </option>
                                    ))}
                            </select>
                        </label>
                        <label>
                            Статус
                            <select name="status">
                                <Options
                                    values={Object.fromEntries(choices.map((status) => [status, statuses[status]]))}
                                />
                            </select>
                        </label>
                    </div>
                    <label>
                        Примечание
                        <textarea
                            name="note"
                            defaultValue={order?.note ?? ''}
                            maxLength={500}
                            disabled={!editable}
                        />
                    </label>
                    <label className="inline">
                        Срочный заказ
                        <input
                            name="urgent"
                            type="checkbox"
                            defaultChecked={order?.urgent ?? false}
                            disabled={!editable}
                        />
                    </label>
                    <label>
                        Теги через запятую
                        <input name="tags" defaultValue={order?.tags.join(', ')} placeholder="опт, самовывоз" />
                    </label>
                    <fieldset>
                        <legend>Позиции заказа</legend>
                        {positions.map((item) => (
                            <div className="position" key={item.key}>
                                <label>
                                    Товар
                                    <select
                                        name="productId"
                                        value={item.productId}
                                        disabled={!editable}
                                        required
                                        onChange={(event) => editPosition(item.key, 'productId', event.target.value)}
                                    >
                                        <option value="">Выберите товар</option>
                                        {catalog
                                            .filter((p) => p.active || String(p.id) === String(item.productId))
                                            .map((product) => (
                                                <option key={product.id} value={product.id}>
                                                    {product.name} · {money(product.price)} · {product.stockQuantity} шт.
                                                </option>
                                            ))}
                                    </select>
                                </label>
                                <label>
                                    Количество
                                    <input
                                        name="quantity"
                                        value={item.quantity}
                                        type="number"
                                        min="1"
                                        max="1000000"
                                        disabled={!editable}
                                        required
                                        onChange={(event) => editPosition(item.key, 'quantity', event.target.value)}
                                    />
                                </label>
                                {editable && (
                                    <button
                                        type="button"
                                        disabled={positions.length === 1}
                                        onClick={() =>
                                            setPositions((current) => current.filter((position) => position.key !== item.key))
                                        }
                                    >
                                        Убрать
                                    </button>
                                )}
                            </div>
                        ))}
                        {editable && (
                            <div className="actions">
                                <button type="button" disabled={positions.length >= 100} onClick={addPosition}>
                                    Добавить товар
                                </button>
                            </div>
                        )}
                    </fieldset>
                    {onCancel && (
                        <button type="button" className="cancel-edit" onClick={onCancel}>
                            Вернуться к заказу
                        </button>
                    )}
                </Form>
            )}
        </Resource>
    );
}
