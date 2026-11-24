import { api, refreshSession, signIn, signOut } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Form } from '../../components/Form';

function PasswordField({ current = false }) {
    const label = current ? 'Текущий пароль' : 'Новый пароль';
    const name = current ? 'currentPassword' : 'newPassword';

    return (
        <label>
            {label}
            <input
                name={name}
                type="password"
                autoComplete={current ? 'current-password' : 'new-password'}
                minLength={current ? undefined : 12}
                maxLength={128}
                required
            />
        </label>
    );
}

export function SignIn() {
    return (
        <section className="auth card">
            <h1>Вход в систему</h1>
            <Form submitText="Войти" onSave={(data) => signIn(Object.fromEntries(data))}>
                <label>
                    Электронная почта
                    <input name="email" type="email" required autoComplete="username" />
                </label>
                <label>
                    Пароль
                    <input name="password" type="password" required autoComplete="current-password" />
                </label>
            </Form>
        </section>
    );
}

export function InitialPassword() {
    async function save(data) {
        await api('/api/auth/change-initial-password', { method: 'POST', body: Object.fromEntries(data) });
        await refreshSession();
    }

    return (
        <section className="auth card">
            <h1>Смените временный пароль</h1>
            <p>Задайте постоянный пароль, чтобы открыть рабочие разделы.</p>
            <Form submitText="Сохранить пароль" onSave={save}>
                <PasswordField />
                <p className="hint">От 12 символов, с заглавной и строчной буквами, цифрой и спецсимволом.</p>
            </Form>
        </section>
    );
}

export function ChangePassword({ onClose, onChanged }) {
    async function save(data) {
        await api('/api/auth/change-password', { method: 'POST', body: Object.fromEntries(data) });
        await signOut();
        onChanged();
    }

    return (
        <Dialog title="Смена пароля" onClose={onClose}>
            <Form onSave={save}>
                <PasswordField current />
                <PasswordField />
            </Form>
        </Dialog>
    );
}
