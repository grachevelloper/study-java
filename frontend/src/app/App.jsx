import { useEffect, useState, useSyncExternalStore } from 'react';
import { getSession, refreshSession, signOut, subscribeToSession } from '../api/client';
import { Message, useAction } from '../components/Feedback';
import { ChangePassword, InitialPassword, SignIn } from '../features/auth/AuthForms';
import { roles } from './labels';
import { Workspace } from './Workspace';

export function App() {
    const session = useSyncExternalStore(subscribeToSession, getSession);
    const action = useAction();
    const user = session?.user;
    const [passwordDialog, setPasswordDialog] = useState(false);
    const [notice, setNotice] = useState('');

    useEffect(() => {
        refreshSession().catch((error) => {
            if (error.status !== 401) setNotice('Не удалось восстановить сеанс. Попробуйте войти.');
        });
    }, []);

    function sessionEnded(message = '') {
        setPasswordDialog(false);
        setNotice(message);
    }

    function leave() {
        action.run(async () => {
            await signOut();
            sessionEnded();
        });
    }

    return (
        <>
            <header className="top">
                <div className="wrap top-inner">
                    <a className="brand" href="/">
                        <span className="brand-mark" aria-hidden="true">
                            ИМ
                        </span>{' '}
                        Интернет-магазин
                    </a>
                    {user && (
                        <div className="account">
                            <span>
                                {user.displayName} · {user.roles.map((role) => roles[role]).join(', ')}
                            </span>
                            {!user.passwordChangeRequired && (
                                <button type="button" className="quiet" onClick={() => setPasswordDialog(true)}>
                                    Сменить пароль
                                </button>
                            )}
                            <button type="button" className="quiet" disabled={action.pending} onClick={leave}>
                                Выйти
                            </button>
                        </div>
                    )}
                </div>
            </header>

            <main className="wrap">
                <Message error>{action.error}</Message>
                {!session && <Message>{notice}</Message>}
                {session === undefined && (
                    <p className="empty" role="status">
                        Загрузка…
                    </p>
                )}
                {session === null && <SignIn />}
                {user?.passwordChangeRequired && <InitialPassword />}
                {user && !user.passwordChangeRequired && (
                    <Workspace session={session} onSessionEnded={sessionEnded} />
                )}
                {passwordDialog && user && !user.passwordChangeRequired && (
                    <ChangePassword
                        onClose={() => setPasswordDialog(false)}
                        onChanged={() => sessionEnded('Пароль изменён. Войдите снова.')}
                    />
                )}
            </main>
        </>
    );
}
