import { useState } from 'react';
import { api, query, signOut } from '../../api/client';
import { useResource } from '../../api/useResource';
import { roles } from '../../app/labels';
import { Dialog } from '../../components/Dialog';
import { Message, Resource } from '../../components/Feedback';
import { Form } from '../../components/Form';
import { Pagination, Table } from '../../components/Table';

function TempPassword() {
    return (
        <label>
            Временный пароль
            <input
                name="temporaryPassword"
                type="password"
                minLength={12}
                maxLength={128}
                required
                autoComplete="new-password"
            />
        </label>
    );
}

function UserForm({ account, onSaved, onClose }) {
    async function save(data) {
        const body = {
            displayName: data.get('displayName'),
            email: data.get('email'),
            roles: data.getAll('roles'),
        };
        if (!body.roles.length) throw new Error('Выберите хотя бы одну роль.');

        if (account.id) {
            body.enabled = data.has('enabled');
            body.version = account.version;
        } else {
            body.temporaryPassword = data.get('temporaryPassword');
        }
        await api(account.id ? `/api/admin/users/${account.id}` : '/api/admin/users', {
            method: account.id ? 'PUT' : 'POST',
            body,
        });
        onSaved('Пользователь сохранён.');
    }

    return (
        <Dialog title={account.id ? 'Редактирование пользователя' : 'Новый пользователь'} onClose={onClose}>
            <Form onSave={save}>
                <label>
                    Имя
                    <input name="displayName" defaultValue={account.displayName} required maxLength={160} />
                </label>
                <label>
                    Логин (email)
                    <input name="email" type="email" defaultValue={account.email} required maxLength={254} />
                </label>
                <fieldset>
                    <legend>Роли</legend>
                    {Object.entries(roles).map(([role, label]) => (
                        <label key={role} className="inline">
                            {label}
                            <input
                                name="roles"
                                value={role}
                                type="checkbox"
                                defaultChecked={account.roles?.includes(role) ?? role === 'VIEWER'}
                            />
                        </label>
                    ))}
                </fieldset>
                {account.id ? (
                    <label className="inline">
                        Активен
                        <input name="enabled" type="checkbox" defaultChecked={account.enabled} />
                    </label>
                ) : (
                    <TempPassword />
                )}
            </Form>
        </Dialog>
    );
}

function ResetPassword({ account, user, onSaved, onClose, onSessionEnded }) {
    async function reset(data) {
        await api(`/api/admin/users/${account.id}/reset-password`, {
            method: 'POST',
            body: Object.fromEntries(data),
        });
        if (account.id === user.id) {
            await signOut();
            onSessionEnded('Пароль сброшен. Войдите с временным паролем.');
        } else {
            onSaved('Временный пароль установлен.');
        }
    }

    return (
        <Dialog title={`Сброс пароля · ${account.displayName}`} onClose={onClose}>
            <Form onSave={reset}>
                <TempPassword />
            </Form>
        </Dialog>
    );
}

export function UsersPage({ active, user, onSessionEnded }) {
    const [page, setPage] = useState(0);
    const [modal, setModal] = useState(null);
    const [notice, setNotice] = useState('');
    const users = useResource(query('/api/admin/users', { page, size: 15 }), active);

    function close() {
        setModal(null);
    }

    function saved(message) {
        close();
        setNotice(message);
        users.reload();
    }

    function row(account) {
        return {
            id: account.id,
            cells: [
                account.displayName,
                account.email,
                account.roles.map((role) => roles[role]).join(', '),
                account.enabled ? 'Активен' : 'Отключён',
                account.passwordChangeRequired ? 'Требуется' : '—',
                <div className="actions">
                    <button type="button" className="link" onClick={() => setModal({ type: 'edit', account })}>
                        Изменить
                    </button>
                    <button type="button" onClick={() => setModal({ type: 'reset', account })}>
                        Сбросить пароль
                    </button>
                </div>,
            ],
        };
    }

    return (
        <>
            <div className="section-heading">
                <h1>Пользователи</h1>
                <button
                    type="button"
                    className="primary"
                    onClick={() => setModal({ type: 'edit', account: {} })}
                >
                    Добавить пользователя
                </button>
            </div>
            <Message>{notice}</Message>
            <Resource resource={users}>
                {(data) => (
                    <Table
                        columns={['Имя', 'Логин', 'Роли', 'Активность', 'Смена пароля', 'Действия']}
                        rows={data.content.map(row)}
                        pagination={<Pagination data={data} onPage={setPage} />}
                    />
                )}
            </Resource>
            {modal?.type === 'edit' && <UserForm account={modal.account} onSaved={saved} onClose={close} />}
            {modal?.type === 'reset' && (
                <ResetPassword
                    account={modal.account}
                    user={user}
                    onSaved={saved}
                    onClose={close}
                    onSessionEnded={onSessionEnded}
                />
            )}
        </>
    );
}
