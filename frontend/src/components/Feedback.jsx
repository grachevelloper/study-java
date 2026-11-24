import { useRef, useState } from 'react';

export function useAction() {
    const active = useRef(false);
    const [pending, setPending] = useState(false);
    const [error, setError] = useState('');

    async function run(action) {
        if (active.current) return;
        active.current = true;
        setPending(true);
        setError('');
        try {
            await action();
        } catch (error) {
            setError(error.message || 'Операция завершилась ошибкой');
        } finally {
            active.current = false;
            setPending(false);
        }
    }

    return { error, pending, run };
}

export function Message({ children, error = false }) {
    if (!children) return null;
    return (
        <p className={error ? 'message error' : 'message'} role={error ? 'alert' : 'status'}>
            {children}
        </p>
    );
}

export function Resource({ resource, children }) {
    if (resource.error) {
        return (
            <>
                <Message error>{resource.error}</Message>
                <button type="button" onClick={resource.reload}>
                    Повторить
                </button>
            </>
        );
    }
    if (resource.data === undefined)
        return (
            <p className="empty" role="status">
                Загрузка…
            </p>
        );
    return children(resource.data);
}
