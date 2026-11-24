let session;
let refreshInFlight;
const subscribers = new Set();

export function subscribeToSession(listener) {
    subscribers.add(listener);
    return () => subscribers.delete(listener);
}

export function getSession() {
    return session;
}

function setSession(value) {
    session = value;
    subscribers.forEach((notify) => notify());
}

class ApiError extends Error {
    constructor(response, body) {
        const fields = Object.values(body?.validationErrors ?? {});
        super([body?.message || `Ошибка запроса (${response.status})`, ...fields].join('\n'));
        this.status = response.status;
    }
}

async function send(path, { method = 'GET', body, format = 'json', signal } = {}, token) {
    const headers = { Accept: format === 'csv' ? 'text/csv, application/json' : 'application/json' };
    if (token) headers.Authorization = `Bearer ${token}`;
    if (body !== undefined && !(body instanceof FormData)) {
        headers['Content-Type'] = 'application/json';
        body = JSON.stringify(body);
    }

    const response = await fetch(path, { method, headers, body, signal, credentials: 'same-origin' });
    if (!response.ok) throw new ApiError(response, await response.json().catch(() => null));
    if (response.status === 204) return null;
    return format === 'csv' ? response.blob() : response.json();
}

export function refreshSession() {
    if (!refreshInFlight) {
        const previous = session;
        refreshInFlight = send('/api/auth/refresh', { method: 'POST' })
            .then((next) => {
                if (getSession() === previous) setSession(next);
            })
            .catch((error) => {
                if (getSession() === previous && (error.status === 401 || previous === undefined)) {
                    setSession(null);
                }
                throw error;
            })
            .finally(() => {
                refreshInFlight = undefined;
            });
    }
    return refreshInFlight;
}

export async function signIn(credentials) {
    const next = await send('/api/auth/login', {
        method: 'POST',
        body: { ...credentials, clientType: 'WEB' },
    });
    setSession(next);
}

export async function signOut() {
    if (refreshInFlight) await refreshInFlight.catch(() => { });
    await send('/api/auth/logout', { method: 'POST' });
    setSession(null);
}

export async function api(path, options) {
    if (session && Date.parse(session.accessExpiresAt) <= Date.now() + 2000) {
        await refreshSession();
    }

    const token = session?.accessToken;
    try {
        return await send(path, options, token);
    } catch (error) {
        if (error.status !== 401 || !token || path.startsWith('/api/auth/') || options?.signal?.aborted) {
            throw error;
        }
        if (session?.accessToken === token) await refreshSession();
        return send(path, options, session?.accessToken);
    }
}

export function query(path, values) {
    const search = new URLSearchParams(Object.entries(values).filter(([, value]) => value !== ''));
    return search.size ? `${path}?${search}` : path;
}
