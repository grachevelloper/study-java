'use strict';

const $ = (id) => document.getElementById(id);
const statusNames = { NEW: 'Новый', PAID: 'Оплачен', SHIPPED: 'Отправлен', DELIVERED: 'Доставлен', CANCELLED: 'Отменён' };
const priorityNames = { HIGH: 'Высокий', NORMAL: 'Обычный', LOW: 'Низкий' };
const roleNames = { VIEWER: 'Просмотр', EDITOR: 'Редактирование', ADMIN: 'Администрирование' };
const statusTargets = { NEW: ['PAID', 'CANCELLED'], PAID: ['SHIPPED', 'CANCELLED'], SHIPPED: ['DELIVERED'] };
const money = new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB' });
const date = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' });
const selected = new Map();

let user = null;
let access = null;
let expiresAt = 0;
let pendingRefresh;
let tab = 'orders';
let orderPage = 0;
let userPage = 0;
let auditPage = 0;
let orderFilters = {};
let auditFilters = {};
let customers = [];

function el(tag, attrs = {}, ...children) {
    const node = document.createElement(tag);
    for (const [name, value] of Object.entries(attrs)) {
        if (value !== undefined && value !== null && value !== false) {
            node.setAttribute(name, value === true ? '' : String(value));
        }
    }
    for (const child of children.flat(Infinity)) {
        if (child !== undefined && child !== null) node.append(child);
    }
    return node;
}

function button(label, action, className = '') {
    const node = el('button', { type: 'button', class: className }, label);
    node.addEventListener('click', () => run(action, node));
    return node;
}

function field(label, control) {
    return el('label', { class: control.type === 'checkbox' ? 'inline' : '' }, label, control);
}

function input(name, value, attributes = {}) {
    return el('input', { name, value, ...attributes });
}

function checkbox(name, checked = false) {
    return input(name, null, { type: 'checkbox', checked });
}

function select(name, values, current, attrs = {}) {
    const node = el('select', { name, ...attrs },
        values.map(([key, label]) => el('option', { value: key }, label)));
    if (current !== undefined && current !== null) node.value = String(current);
    return node;
}

function dialog(title, ...contents) {
    $('dialog-title').textContent = title;
    $('dialog-content').replaceChildren(...contents);
    message('', false, $('dialog-message'));
    if (!$('dialog').open) $('dialog').showModal();
}

function message(text, error = false, target = $('dialog').open ? $('dialog-message') : $('message')) {
    target.textContent = text;
    target.hidden = !text;
    target.classList.toggle('error', error);
}

async function run(action, source) {
    if (source?.disabled) return;
    if (source) source.disabled = true;
    message('');
    try {
        await action();
    } catch (error) {
        message(error.message || 'Операция завершилась ошибкой', true);
    } finally {
        if (source) source.disabled = false;
    }
}

function onClick(id, handler) {
    const node = $(id);
    node.addEventListener('click', () => run(handler, node));
}

function onSubmit(form, action) {
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        run(() => action(new FormData(form)), event.submitter);
    });
}

function submit(label = 'Сохранить') {
    return el('div', { class: 'actions' }, el('button', { type: 'submit', class: 'primary' }, label));
}

function editor(fields, action, label) {
    const form = el('form', {}, fields, submit(label));
    onSubmit(form, action);
    return form;
}

function query(path, args) {
    const parameters = new URLSearchParams(Object.entries(args).filter(([, value]) => value !== ''));
    return parameters.size ? path + '?' + parameters : path;
}

function hasAdmin() {
    return user?.roles.includes('ADMIN');
}

function canEdit() {
    return hasAdmin() || user?.roles.includes('EDITOR');
}

class ApiError extends Error {
    constructor(response, body) {
        const fields = Object.values(body?.validationErrors || {});
        const text = body?.message || 'Ошибка запроса (' + response.status + ')';
        super(fields.length ? text + '\n' + fields.join('\n') : text);
        this.status = response.status;
    }
}

async function send(path, method = 'GET', body, token) {
    const headers = { Accept: path.startsWith('/api/orders/export') ? 'text/csv, application/json' : 'application/json' };
    if (token) headers.Authorization = 'Bearer ' + token;
    if (body !== undefined && !(body instanceof FormData)) {
        headers['Content-Type'] = 'application/json';
        body = JSON.stringify(body);
    }
    const response = await fetch(path, { method, headers, body, credentials: 'same-origin' });
    if (!response.ok) {
        throw new ApiError(response, await response.json().catch(() => null));
    }
    if (response.status === 204) return null;
    return response.headers.get('Content-Type')?.includes('text/csv') ? response.blob() : response.json();
}

function setSession(session) {
    user = session.user;
    access = session.accessToken;
    expiresAt = Date.parse(session.accessExpiresAt);
}

async function refresh() {
    if (!pendingRefresh) {
        pendingRefresh = send('/api/auth/refresh', 'POST').then(setSession).finally(() => {
            pendingRefresh = null;
        });
    }
    return pendingRefresh;
}

async function api(path, method = 'GET', body) {
    try {
        if (access && Date.now() >= expiresAt - 2000) await refresh();
        const token = access;
        try {
            return await send(path, method, body, token);
        } catch (error) {
            if (error.status !== 401 || path.startsWith('/api/auth/')) throw error;
            if (access === token) await refresh();
            return await send(path, method, body, access);
        }
    } catch (error) {
        if (error.status === 401 && !path.startsWith('/api/auth/')) leave();
        throw error;
    }
}

function paintSession() {
    $('account').hidden = !user;
    $('account-name').textContent = user ? user.displayName + ' · ' + user.roles.map(r => roleNames[r]).join(', ') : '';
    $('account-password').hidden = !user || user.passwordChangeRequired;
    $('login').hidden = !!user;
    $('initial-password').hidden = !user?.passwordChangeRequired;
    $('workspace').hidden = !user || user.passwordChangeRequired;
    document.querySelectorAll('[data-admin]').forEach(node => node.hidden = !hasAdmin());
    document.querySelectorAll('[data-edit]').forEach(node => node.hidden = !canEdit());
}

function leave() {
    user = access = null;
    expiresAt = 0;
    tab = 'orders';
    orderPage = userPage = auditPage = 0;
    orderFilters = auditFilters = {};
    selected.clear();
    $('filters').reset();
    $('audit-filters').reset();
    if ($('dialog').open) $('dialog').close();
    paintSession();
}

async function enter() {
    paintSession();
    if (!user || user.passwordChangeRequired) return;
    await loadCustomers();
    await openTab(tab);
}

function time(value) {
    if (!value) return '—';
    const iso = /(?:Z|[+-]\d\d:\d\d)$/.test(value) ? value : value + 'Z';
    return date.format(new Date(iso));
}

function table(host, headers, rows, footer) {
    if (!rows.length) {
        host.replaceChildren(el('div', { class: 'empty' }, 'Записей нет'), footer || '');
        return;
    }
    host.replaceChildren(el('div', { class: 'table-scroll' },
        el('table', {},
            el('thead', {}, el('tr', {}, headers.map(title => el('th', { scope: 'col' }, title)))),
            el('tbody', {}, rows.map(row => el('tr', {}, row.map(value => {
                return value instanceof HTMLTableCellElement ? value : el('td', {}, value ?? '—');
            }))))
        )), footer || '');
}

function pager(page, change) {
    const previous = button('Назад', () => change(page.page - 1));
    const next = button('Дальше', () => change(page.page + 1));
    previous.disabled = page.first;
    next.disabled = page.last;
    const label = page.totalPages ? 'Страница ' + (page.page + 1) + ' из ' + page.totalPages + ' · ' : '';
    return el('div', { class: 'paging' }, el('span', {}, label + 'Записей: ' + page.totalElements),
        el('div', { class: 'actions' }, previous, next));
}

async function openTab(name) {
    tab = name;
    document.querySelectorAll('.view').forEach(view => view.hidden = view.id !== name);
    document.querySelectorAll('[data-tab]').forEach(node => {
        if (node.dataset.tab === name) node.setAttribute('aria-current', 'page');
        else node.removeAttribute('aria-current');
    });
    await ({ orders: loadOrders, customers: showCustomers, users: loadUsers, audit: loadAudit, analytics: loadAnalytics })[name]();
}

async function loadCustomers() {
    customers = await api('/api/customers');
    const filter = $('customer-filter');
    const value = filter.value;
    filter.replaceChildren(el('option', { value: '' }, 'Все покупатели'),
        ...customers.map(c => el('option', { value: c.id }, c.fullName)));
    filter.value = value;
}

async function loadOrders() {
    const data = await api(query('/api/orders', { ...orderFilters, page: orderPage, size: 15 }));
    if (orderPage > 0 && orderPage >= data.totalPages) {
        orderPage = Math.max(0, data.totalPages - 1);
        return loadOrders();
    }
    const headers = ['Номер', 'Покупатель', 'Статус', 'Сумма', 'Приоритет', 'Теги', 'Изменён'];
    if (canEdit()) headers.unshift('Выбор');
    const rows = data.content.map(order => {
        const number = button(order.externalId, () => openOrder(order.id), 'link');
        const row = [number, order.customerName,
            el('span', { class: 'badge' }, statusNames[order.status] + (order.archived ? ' · Архив' : '')),
            el('td', { class: 'numeric' }, money.format(order.total)),
            el('span', { class: order.urgent ? 'badge urgent' : 'badge' }, priorityNames[order.priority]),
            order.tags.join(', ') || '—', time(order.updatedAt)];
        if (canEdit()) {
            const toggle = checkbox('order', selected.has(order.id));
            toggle.setAttribute('aria-label', 'Выбрать заказ ' + order.externalId);
            toggle.addEventListener('change', () => {
                if (toggle.checked) selected.set(order.id, order.externalId);
                else selected.delete(order.id);
                renderSelection();
            });
            row.unshift(toggle);
        }
        return row;
    });
    table($('order-table'), headers, rows, pager(data, page => {
        orderPage = page;
        return loadOrders();
    }));
    renderSelection();
}

function renderSelection() {
    const host = $('selected-orders');
    host.hidden = !selected.size || !canEdit();
    host.replaceChildren(el('strong', {}, 'Выбрано: ' + selected.size),
        button('Массовое действие', bulkDialog, 'primary'),
        button('Снять выбор', async () => {
            selected.clear();
            await loadOrders();
        }));
}

function readFilters(formData) {
    const values = Object.fromEntries(formData);
    if (values.total === '') delete values.totalOp;
    return values;
}

function bulkDialog() {
    const actions = [
        ['ARCHIVE', 'Архивировать'], ['RESTORE', 'Восстановить'], ['CHANGE_STATUS', 'Изменить статус'],
        ['ADD_TAG', 'Добавить тег'], ['REMOVE_TAG', 'Удалить тег'],
    ];
    const action = select('action', actions);
    const status = select('status', Object.entries(statusNames));
    const tag = input('tag', '', { maxlength: 50 });
    const statusField = field('Новый статус', status);
    const tagField = field('Тег', tag);
    function mode() {
        statusField.hidden = action.value !== 'CHANGE_STATUS';
        tagField.hidden = !['ADD_TAG', 'REMOVE_TAG'].includes(action.value);
        tag.required = !tagField.hidden;
    }
    action.addEventListener('change', mode);
    mode();
    dialog('Массовое действие', editor([field('Действие', action), statusField, tagField], async form => {
        const request = { ids: [...selected.keys()], action: form.get('action') };
        if (!statusField.hidden) request.status = status.value;
        if (!tagField.hidden) request.tag = tag.value.trim();
        const result = await api('/api/orders/bulk', 'POST', request);
        const errors = result.errors.map(item => (selected.get(item.id) || item.id) + ' — ' + item.message);
        result.succeeded.forEach(id => selected.delete(id));
        $('dialog').close();
        await loadOrders();
        message(['Успешно: ' + result.succeeded.length + ' · Ошибок: ' + errors.length, ...errors].join('\n'), errors.length > 0);
    }, 'Применить'));
}

async function openOrder(id) {
    const [detail, notes] = await Promise.all([api('/api/orders/' + id), api('/api/orders/' + id + '/notes')]);
    const order = detail.order;
    const actions = el('div', { class: 'actions' });
    if (canEdit() && !order.archived) actions.append(button('Редактировать', () => orderEditor(detail), 'primary'));
    if (canEdit()) {
        actions.append(button(order.archived ? 'Восстановить' : 'В архив', async () => {
            await api('/api/orders/' + id + (order.archived ? '/restore' : ''), order.archived ? 'POST' : 'DELETE');
            await loadOrders();
            await openOrder(id);
        }));
    }
    if (hasAdmin() && order.archived && ['NEW', 'CANCELLED'].includes(order.status)) {
        actions.append(button('Удалить окончательно', async () => {
            if (!confirm('Окончательно удалить заказ ' + order.externalId + '?')) return;
            await api('/api/orders/' + id + '/permanent', 'DELETE');
            selected.delete(id);
            $('dialog').close();
            await loadOrders();
            message('Заказ удалён.');
        }, 'danger'));
    }
    const positions = el('div', { class: 'card' });
    table(positions, ['Товар', 'Кол-во', 'Цена'], detail.items.map(item =>
        [item.name, String(item.quantity), money.format(item.unitPrice)]));
    const notesHeading = el('div', { class: 'section-heading' }, el('h3', {}, 'Заметки'),
        canEdit() && !order.archived ? button('Добавить заметку', () => noteEditor(id)) : '');
    const noteCards = notes.map(note => el('article', { class: 'card note' },
        el('div', { class: 'hint' }, note.authorName + ' · ' + time(note.createdAt) + (note.pinned ? ' · Закреплена' : '')),
        el('p', {}, note.body),
        canEdit() && !order.archived && (hasAdmin() || note.authorId === user.id)
            ? el('div', { class: 'actions' }, button('Изменить', () => noteEditor(id, note)),
                button('Удалить', async () => {
                    if (confirm('Удалить заметку?')) {
                        await api('/api/orders/' + id + '/notes/' + note.id, 'DELETE');
                        await openOrder(id);
                    }
                }, 'danger')) : ''));
    dialog('Заказ ' + order.externalId, el('div', { class: 'stack' },
        el('div', { class: 'actions' }, order.customerName,
            el('span', { class: 'badge' }, statusNames[order.status]),
            el('span', { class: order.urgent ? 'badge urgent' : 'badge' }, priorityNames[order.priority]),
            order.archived ? el('span', { class: 'badge' }, 'В архиве') : '', el('strong', {}, money.format(order.total))),
        el('p', { class: 'hint' }, 'Создан ' + time(order.createdAt) + ' · Изменён ' + time(order.updatedAt)),
        order.note ? el('div', {}, order.note) : '', order.tags.length ? el('div', {}, 'Теги: ' + order.tags.join(', ')) : '',
        positions, actions, notesHeading, noteCards.length ? noteCards : el('p', { class: 'hint' }, 'Пока нет заметок.')));
}

async function orderEditor(detail) {
    const order = detail?.order;
    const isNew = !order || order.status === 'NEW';
    const products = await api('/api/products');
    const customer = select('customerId', [['', 'Выберите покупателя'], ...customers
        .filter(c => c.active || c.id === order?.customerId).map(c => [c.id, c.fullName + (c.active ? '' : ' · отключён')])],
    order?.customerId ?? '', { required: true, disabled: !isNew });
    const code = input('externalId', order?.externalId ?? '', { required: true, disabled: !isNew,
        pattern: '[A-Za-z0-9][A-Za-z0-9._\\-]{0,99}', maxlength: 100 });
    const note = el('textarea', { name: 'note', maxlength: 500, disabled: !isNew }, order?.note ?? '');
    const urgent = checkbox('urgent', order?.urgent);
    urgent.disabled = !isNew;
    const tags = input('tags', order?.tags.join(', ') ?? '', { placeholder: 'опт, самовывоз' });
    const statusCodes = order ? [order.status, ...(statusTargets[order.status] || [])] : ['NEW'];
    const status = select('status', statusCodes.map(value => [value, statusNames[value]]));
    const positions = el('fieldset', {}, el('legend', {}, 'Позиции заказа'));
    const rows = el('div', { class: 'stack' });
    positions.append(rows);
    function add(position) {
        const product = select('productId', [['', 'Выберите товар'], ...products
            .filter(p => p.active || p.id === position?.productId)
            .map(p => [p.id, p.name + ' · ' + money.format(p.price) + ' · ' + p.stockQuantity + ' шт.'])],
        position?.productId ?? '', { disabled: !isNew, required: true });
        const quantity = input('quantity', position?.quantity ?? 1,
            { type: 'number', min: 1, max: 1000000, required: true, disabled: !isNew });
        const row = el('div', { class: 'position' }, field('Товар', product), field('Количество', quantity));
        if (isNew) row.append(button('Убрать', () => {
            if (rows.children.length > 1) row.remove();
        }));
        row.read = () => ({ productId: Number(product.value), quantity: Number(quantity.value) });
        rows.append(row);
    }
    if (detail?.items.length) detail.items.forEach(add);
    else add();
    if (isNew) positions.append(el('div', { class: 'actions' }, button('Добавить товар', () => {
        if (rows.children.length < 100) add();
    })));

    const form = editor([
        el('div', { class: 'form-grid' }, field('Номер заказа', code), field('Покупатель', customer), field('Статус', status)),
        field('Примечание', note), field('Срочный заказ', urgent), field('Теги через запятую', tags), positions,
    ], async () => {
        const payload = { externalId: code.value, customerId: Number(customer.value), note: note.value, urgent: urgent.checked,
            status: status.value, tags: tags.value.split(',').map(s => s.trim()).filter(Boolean), items: [...rows.children].map(row => row.read()) };
        if (order) payload.version = order.version;
        const saved = await api('/api/orders' + (order ? '/' + order.id : ''), order ? 'PUT' : 'POST', payload);
        await loadOrders();
        await openOrder(saved.order.id);
        message(order ? 'Заказ изменён.' : 'Заказ создан.');
    });
    dialog(order ? 'Редактирование заказа ' + order.externalId : 'Новый заказ', form);
}

function noteEditor(id, note) {
    const body = el('textarea', { name: 'body', required: true, maxlength: 4000 }, note?.body || '');
    const pinned = checkbox('pinned', note?.pinned);
    dialog(note ? 'Редактирование заметки' : 'Новая заметка', editor([field('Текст', body), field('Закрепить', pinned)], async () => {
        const request = { body: body.value, pinned: pinned.checked };
        if (note) request.version = note.version;
        await api('/api/orders/' + id + '/notes' + (note ? '/' + note.id : ''), note ? 'PUT' : 'POST', request);
        await openOrder(id);
        message('Заметка сохранена.');
    }));
}

function importDialog() {
    const file = input('file', null, { type: 'file', required: true, accept: '.csv,text/csv' });
    const mode = select('mode', [['CREATE_ONLY', 'Только новые заказы'], ['UPSERT_BY_EXTERNAL_ID', 'Добавить или обновить по номеру']]);
    const result = el('div', { class: 'stack' });
    let ready = false;

    async function upload(endpoint) {
        const form = new FormData();
        form.set('file', file.files[0]);
        return api(query('/api/orders/import/' + endpoint, { mode: mode.value }), 'POST', form);
    }

    const apply = button('Применить импорт', async () => {
        if (!ready || !file.files.length) return;
        const data = await upload('apply');
        ready = false;
        apply.hidden = true;
        formatImport(result, 'Создано: ' + data.created + ' · Обновлено: ' + data.updated + ' · Пропущено: ' + data.skipped, data.issues);
        await loadOrders();
    }, 'primary');
    apply.hidden = true;

    for (const control of [file, mode]) {
        control.addEventListener('change', () => {
            ready = false;
            apply.hidden = true;
            result.replaceChildren();
        });
    }
    const preview = editor([field('CSV-файл', file), field('Режим импорта', mode),
        el('p', { class: 'hint' }, 'Столбцы: external_id, customer_email, note, urgent, tags, items, status. Позиции — SKU:количество через |; теги — через |.')],
    async () => {
        const data = await upload('preview');
        formatImport(result, 'Корректных строк: ' + data.validRows + ' · С ошибками: ' + data.invalidRows, data.issues);
        ready = data.validRows > 0;
        apply.hidden = !ready;
    }, 'Проверить файл');

    dialog('Импорт CSV', el('div', { class: 'stack' }, preview, result, el('div', { class: 'actions' }, apply)));
}

function formatImport(host, summary, issues) {
    host.replaceChildren(el('strong', {}, summary),
        issues.length ? el('ul', {}, issues.map(issue => el('li', {}, 'Строка ' + issue.row + ' — ' + issue.message))) : '');
}

async function exportOrders() {
    const blob = await api(query('/api/orders/export', orderFilters));
    const uri = URL.createObjectURL(blob);
    const link = el('a', { href: uri, download: 'orders.csv' });
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(uri), 1000);
}

async function loadAnalytics() {
    const data = await api('/api/analytics/summary');
    const metrics = [['Всего заказов', data.total], ['Активные', data.active], ['Архивные', data.archived],
        ['Срочные', data.urgent], ['Средняя сумма', money.format(data.averageTotal)]];
    function group(title, entries) {
        return el('section', { class: 'card group' }, el('h3', {}, title),
            entries.length ? entries.map(([name, count]) =>
                el('div', { class: 'summary-row' }, el('span', {}, name), el('strong', {}, String(count))))
                : el('p', { class: 'hint' }, 'Нет записей'));
    }
    $('analytics-content').replaceChildren(
        el('div', { class: 'metrics' }, metrics.map(([name, value]) =>
            el('div', { class: 'card metric' }, el('span', { class: 'hint' }, name), el('strong', {}, String(value))))),
        el('div', { class: 'groups' },
            group('По покупателям', data.byCustomer.map(c => [c.name, c.count])),
            group('По статусам', Object.entries(data.byStatus).map(([status, count]) => [statusNames[status], count])),
            group('По приоритету', Object.entries(data.byPriority).map(([priority, count]) => [priorityNames[priority], count]))));
}

async function showCustomers() {
    await loadCustomers();
    table($('customers-table'), ['Имя', 'Электронная почта', 'Телефон', 'Активность', 'Действия'],
        customers.map(customer => [customer.fullName, customer.email, customer.phone, customer.active ? 'Активен' : 'Отключён',
            el('div', { class: 'actions' }, button('Изменить', () => customerEditor(customer), 'link'),
                button(customer.active ? 'Отключить' : 'Включить', async () => {
                    await api(query('/api/customers/' + customer.id + '/active', { active: !customer.active }), 'PATCH');
                    await showCustomers();
                }))]));
}

function customerEditor(customer) {
    const fields = [
        field('Имя', input('fullName', customer?.fullName, { maxlength: 200, required: true })),
        field('Электронная почта', input('email', customer?.email, { type: 'email', maxlength: 320, required: true })),
        field('Телефон', input('phone', customer?.phone, { type: 'tel', maxlength: 50 })),
    ];
    dialog(customer ? 'Редактирование покупателя' : 'Новый покупатель', editor(fields, async form => {
        await api('/api/customers' + (customer ? '/' + customer.id : ''), customer ? 'PUT' : 'POST', Object.fromEntries(form));
        $('dialog').close();
        await showCustomers();
        message('Покупатель сохранён.');
    }));
}

async function loadUsers() {
    const data = await api(query('/api/admin/users', { page: userPage, size: 15 }));
    table($('users-table'), ['Имя', 'Логин', 'Роли', 'Активность', 'Смена пароля', 'Действия'],
        data.content.map(account => [account.displayName, account.email, account.roles.map(role => roleNames[role]).join(', '),
            account.enabled ? 'Активен' : 'Отключён', account.passwordChangeRequired ? 'Требуется' : '—',
            el('div', { class: 'actions' }, button('Изменить', () => userEditor(account), 'link'),
                button('Сбросить пароль', () => resetPassword(account)))]),
        pager(data, page => {
            userPage = page;
            return loadUsers();
        }));
}

function userEditor(account) {
    const roles = el('fieldset', {}, el('legend', {}, 'Роли'));
    for (const [value, label] of Object.entries(roleNames)) {
        const toggle = checkbox('roles', account ? account.roles.includes(value) : value === 'VIEWER');
        toggle.value = value;
        roles.append(field(label, toggle));
    }
    const fields = [
        field('Имя', input('displayName', account?.displayName, { required: true, maxlength: 160 })),
        field('Логин (email)', input('email', account?.email, { required: true, type: 'email', maxlength: 254 })),
        roles,
        account ? field('Активен', checkbox('enabled', account.enabled))
            : field('Временный пароль', input('temporaryPassword', '', { type: 'password', required: true, minlength: 12, autocomplete: 'new-password' })),
    ];
    dialog(account ? 'Редактирование пользователя' : 'Новый пользователь', editor(fields, async form => {
        const request = { displayName: form.get('displayName'), email: form.get('email'), roles: form.getAll('roles') };
        if (!request.roles.length) throw new Error('Выберите хотя бы одну роль.');
        if (account) {
            request.enabled = form.has('enabled');
            request.version = account.version;
        } else {
            request.temporaryPassword = form.get('temporaryPassword');
        }
        await api('/api/admin/users' + (account ? '/' + account.id : ''), account ? 'PUT' : 'POST', request);
        $('dialog').close();
        await loadUsers();
        message('Пользователь сохранён.');
    }));
}

function resetPassword(account) {
    const password = input('temporaryPassword', '', { type: 'password', required: true, minlength: 12, autocomplete: 'new-password' });
    dialog('Сброс пароля · ' + account.displayName, editor([field('Временный пароль', password)], async form => {
        await api('/api/admin/users/' + account.id + '/reset-password', 'POST', Object.fromEntries(form));
        $('dialog').close();
        if (account.id === user.id) {
            await send('/api/auth/logout', 'POST');
            leave();
            message('Пароль сброшен. Войдите с временным паролем.');
        } else {
            await loadUsers();
            message('Временный пароль установлен.');
        }
    }));
}

async function loadAudit() {
    const data = await api(query('/api/admin/audit', { ...auditFilters, page: auditPage, size: 15 }));
    table($('audit-table'), ['Время', 'Пользователь', 'Событие', 'Описание', 'Запись', 'ID запроса'],
        data.content.map(event => [time(event.occurredAt), event.actorEmail, event.eventType, event.summary,
            event.entityId || event.entityType, event.correlationId]),
        pager(data, page => {
            auditPage = page;
            return loadAudit();
        }));
}

function changePassword() {
    const fields = [
        field('Текущий пароль', input('currentPassword', '', { type: 'password', required: true, autocomplete: 'current-password' })),
        field('Новый пароль', input('newPassword', '', { type: 'password', required: true, minlength: 12, autocomplete: 'new-password' })),
    ];
    dialog('Смена пароля', editor(fields, async form => {
        await api('/api/auth/change-password', 'POST', Object.fromEntries(form));
        await send('/api/auth/logout', 'POST');
        leave();
        message('Пароль изменён. Войдите снова.');
    }));
}

onSubmit($('login-form'), async form => {
    setSession(await send('/api/auth/login', 'POST', { ...Object.fromEntries(form), clientType: 'WEB' }));
    $('login-form').reset();
    await enter();
});
onSubmit($('initial-password-form'), async form => {
    await api('/api/auth/change-initial-password', 'POST', Object.fromEntries(form));
    $('initial-password-form').reset();
    await refresh();
    await enter();
});
onSubmit($('filters'), async form => {
    orderFilters = readFilters(form);
    selected.clear();
    orderPage = 0;
    await loadOrders();
});
$('filters').addEventListener('reset', () => {
    setTimeout(() => run(() => {
        if (!user) return;
        orderFilters = {};
        selected.clear();
        orderPage = 0;
        return loadOrders();
    }), 0);
});
onSubmit($('audit-filters'), async form => {
    auditFilters = Object.fromEntries(form);
    auditPage = 0;
    await loadAudit();
});
$('status-filter').append(...Object.entries(statusNames).map(([value, label]) => el('option', { value }, label)));
$('priority-filter').append(...Object.entries(priorityNames).map(([value, label]) => el('option', { value }, label)));
document.querySelectorAll('[data-tab]').forEach(node => node.addEventListener('click', () => run(() => openTab(node.dataset.tab))));
onClick('account-password', changePassword);
onClick('logout', async () => {
    await send('/api/auth/logout', 'POST');
    leave();
});
$('swagger').addEventListener('click', event => {
    if (Date.now() < expiresAt - 2000) return;
    event.preventDefault();
    run(async () => {
        await refresh();
        window.location.assign('/swagger-ui.html');
    });
});
onClick('close-dialog', () => $('dialog').close());
onClick('new-order', () => orderEditor());
onClick('new-customer', () => customerEditor());
onClick('new-user', () => userEditor());
onClick('import', importDialog);
onClick('export', exportOrders);

(async () => {
    try {
        await refresh();
    } catch (error) {
        if (error.status !== 401) message(error.message, true);
    }
    await run(enter);
})();
