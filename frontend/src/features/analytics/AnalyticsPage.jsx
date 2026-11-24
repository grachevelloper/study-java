import { useResource } from '../../api/useResource';
import { money, priorities, statuses } from '../../app/labels';
import { Resource } from '../../components/Feedback';

function Group({ title, rows }) {
    return (
        <section className="card group">
            <h3>{title}</h3>
            {rows.length ? (
                rows.map(({ id, name, count }) => (
                    <div className="summary-row" key={id}>
                        <span>{name}</span>
                        <strong>{count}</strong>
                    </div>
                ))
            ) : (
                <p className="hint">Нет записей</p>
            )}
        </section>
    );
}

export function AnalyticsPage({ active }) {
    const analytics = useResource('/api/analytics/summary', active);

    return (
        <>
            <h1>Аналитика</h1>
            <Resource resource={analytics}>
                {(data) => {
                    const metrics = {
                        'Всего заказов': data.total,
                        Активные: data.active,
                        Архивные: data.archived,
                        Срочные: data.urgent,
                        'Средняя сумма': money(data.averageTotal),
                    };
                    return (
                        <>
                            <div className="metrics">
                                {Object.entries(metrics).map(([name, value]) => (
                                    <div className="card metric" key={name}>
                                        <span className="hint">{name}</span>
                                        <strong>{value}</strong>
                                    </div>
                                ))}
                            </div>
                            <div className="groups">
                                <Group
                                    title="По покупателям"
                                    rows={data.byCustomer.map((row) => ({ ...row, id: row.customerId }))}
                                />
                                <Group
                                    title="По статусам"
                                    rows={Object.entries(data.byStatus).map(([id, count]) => ({
                                        id,
                                        name: statuses[id],
                                        count,
                                    }))}
                                />
                                <Group
                                    title="По приоритету"
                                    rows={Object.entries(data.byPriority).map(([id, count]) => ({
                                        id,
                                        name: priorities[id],
                                        count,
                                    }))}
                                />
                            </div>
                        </>
                    );
                }}
            </Resource>
        </>
    );
}
