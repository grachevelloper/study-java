import { useState } from 'react';
import { api, query } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Message, Resource, useAction } from '../../components/Feedback';
import { Form } from '../../components/Form';
import { Table } from '../../components/Table';

function CustomerForm({ customer, onSaved, onClose }) {
    async function save(data) {
        await api(customer.id ? `/api/customers/${customer.id}` : '/api/customers', {
            method: customer.id ? 'PUT' : 'POST',
            body: Object.fromEntries(data),
        });
        onSaved();
    }

    return (
        <Dialog title={customer.id ? 'Редактирование покупателя' : 'Новый покупатель'} onClose={onClose}>
            <Form onSave={save}>
                <label>
                    Имя
                    <input name="fullName" defaultValue={customer.fullName} required maxLength={200} />
                </label>
                <label>
                    Электронная почта
                    <input name="email" type="email" defaultValue={customer.email} required maxLength={320} />
                </label>
                <label>
                    Телефон
                    <input name="phone" type="tel" defaultValue={customer.phone} maxLength={50} />
                </label>
            </Form>
        </Dialog>
    );
}

export function CustomersPage({ customers }) {
    const [editing, setEditing] = useState(null);
    const [notice, setNotice] = useState('');
    const action = useAction();

    function toggle(customer) {
        action.run(async () => {
            await api(query(`/api/customers/${customer.id}/active`, { active: !customer.active }), {
                method: 'PATCH',
            });
            customers.reload();
            setNotice(customer.active ? 'Покупатель отключён.' : 'Покупатель включён.');
        });
    }

    function row(customer) {
        return {
            id: customer.id,
            cells: [
                customer.fullName,
                customer.email,
                customer.phone,
                customer.active ? 'Активен' : 'Отключён',
                <div className="actions">
                    <button type="button" className="link" onClick={() => setEditing(customer)}>
                        Изменить
                    </button>
                    <button type="button" disabled={action.pending} onClick={() => toggle(customer)}>
                        {customer.active ? 'Отключить' : 'Включить'}
                    </button>
                </div>,
            ],
        };
    }

    return (
        <>
            <div className="section-heading">
                <h1>Покупатели</h1>
                <button type="button" className="primary" onClick={() => setEditing({})}>
                    Добавить покупателя
                </button>
            </div>
            <Message error>{action.error}</Message>
            <Message>{notice}</Message>
            <Resource resource={customers}>
                {(data) => (
                    <Table
                        columns={['Имя', 'Электронная почта', 'Телефон', 'Активность', 'Действия']}
                        rows={data.map(row)}
                    />
                )}
            </Resource>
            {editing && (
                <CustomerForm
                    customer={editing}
                    onClose={() => setEditing(null)}
                    onSaved={() => {
                        setEditing(null);
                        setNotice('Покупатель сохранён.');
                        customers.reload();
                    }}
                />
            )}
        </>
    );
}
