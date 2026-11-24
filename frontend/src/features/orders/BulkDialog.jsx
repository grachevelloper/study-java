import { useState } from 'react';
import { api } from '../../api/client';
import { statuses } from '../../app/labels';
import { Dialog } from '../../components/Dialog';
import { Form, Options } from '../../components/Form';

const actions = {
    ARCHIVE: 'Архивировать',
    RESTORE: 'Восстановить',
    CHANGE_STATUS: 'Изменить статус',
    ADD_TAG: 'Добавить тег',
    REMOVE_TAG: 'Удалить тег',
};

export function BulkDialog({ selected, onApplied, onClose }) {
    const [operation, setOperation] = useState('ARCHIVE');
    const needsTag = ['ADD_TAG', 'REMOVE_TAG'].includes(operation);

    async function apply(data) {
        const body = { ids: [...selected.keys()], action: operation };
        if (operation === 'CHANGE_STATUS') body.status = data.get('status');
        if (needsTag) body.tag = data.get('tag').trim();
        onApplied(await api('/api/orders/bulk', { method: 'POST', body }));
    }

    return (
        <Dialog title="Массовое действие" onClose={onClose}>
            <Form submitText="Применить" onSave={apply}>
                <label>
                    Действие
                    <select name="action" value={operation} onChange={(event) => setOperation(event.target.value)}>
                        <Options values={actions} />
                    </select>
                </label>
                {operation === 'CHANGE_STATUS' && (
                    <label>
                        Новый статус
                        <select name="status">
                            <Options values={statuses} />
                        </select>
                    </label>
                )}
                {needsTag && (
                    <label>
                        Тег
                        <input name="tag" maxLength={50} required />
                    </label>
                )}
            </Form>
        </Dialog>
    );
}
