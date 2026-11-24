export const roles = { VIEWER: 'Просмотр', EDITOR: 'Редактирование', ADMIN: 'Администрирование' };
export const statuses = {
    NEW: 'Новый',
    PAID: 'Оплачен',
    SHIPPED: 'Отправлен',
    DELIVERED: 'Доставлен',
    CANCELLED: 'Отменён',
};
export const priorities = { HIGH: 'Высокий', NORMAL: 'Обычный', LOW: 'Низкий' };

const currency = new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB' });
const dateTime = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' });

export function money(value) {
    return currency.format(value);
}

export function time(value) {
    if (!value) return '—';
    const iso = /(?:Z|[+-]\d\d:\d\d)$/.test(value) ? value : `${value}Z`;
    return dateTime.format(new Date(iso));
}
