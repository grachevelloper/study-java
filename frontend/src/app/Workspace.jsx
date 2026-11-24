import { useState } from 'react';
import { refreshSession } from '../api/client';
import { useResource } from '../api/useResource';
import { Message, useAction } from '../components/Feedback';
import { AuditPage } from '../features/admin/AuditPage';
import { CustomersPage } from '../features/admin/CustomersPage';
import { UsersPage } from '../features/admin/UsersPage';
import { AnalyticsPage } from '../features/analytics/AnalyticsPage';
import { OrdersPage } from '../features/orders/OrdersPage';

const sections = [
    { id: 'orders', name: 'Заказы' },
    { id: 'analytics', name: 'Аналитика' },
    { id: 'customers', name: 'Покупатели', admin: true },
    { id: 'users', name: 'Пользователи', admin: true },
    { id: 'audit', name: 'Аудит', admin: true },
];

export function Workspace({ session, onSessionEnded }) {
    const { user } = session;
    const isAdmin = user.roles.includes('ADMIN');
    const canEdit = isAdmin || user.roles.includes('EDITOR');
    const allowed = sections.filter((section) => !section.admin || isAdmin);
    const [opened, setOpened] = useState(() => new Set(['orders']));
    const [tab, setTab] = useState('orders');
    const active = allowed.some((section) => section.id === tab) ? tab : 'orders';
    const customers = useResource('/api/customers');
    const swagger = useAction();

    function open(id) {
        setOpened((current) => new Set(current).add(id));
        setTab(id);
    }

    function openSwagger(event) {
        if (Date.parse(session.accessExpiresAt) > Date.now() + 2000) return;
        event.preventDefault();
        swagger.run(async () => {
            await refreshSession();
            window.location.assign('/swagger-ui.html');
        });
    }

    const pages = {
        orders: <OrdersPage user={user} canEdit={canEdit} isAdmin={isAdmin} customers={customers} />,
        analytics: <AnalyticsPage active={active === 'analytics'} />,
        customers: <CustomersPage customers={customers} />,
        users: <UsersPage active={active === 'users'} user={user} onSessionEnded={onSessionEnded} />,
        audit: <AuditPage active={active === 'audit'} />,
    };

    return (
        <>
            <nav aria-label="Разделы">
                {allowed.map(({ id, name }) => (
                    <button
                        type="button"
                        key={id}
                        aria-current={active === id ? 'page' : undefined}
                        onClick={() => open(id)}
                    >
                        {name}
                    </button>
                ))}
                {isAdmin && (
                    <a href="/swagger-ui.html" target="_blank" rel="noopener" onClick={openSwagger}>
                        Swagger
                    </a>
                )}
            </nav>
            <Message error>{swagger.error}</Message>
            {allowed
                .filter((section) => opened.has(section.id))
                .map(({ id }) => (
                    <section key={id} className="view" hidden={active !== id}>
                        {pages[id]}
                    </section>
                ))}
        </>
    );
}
