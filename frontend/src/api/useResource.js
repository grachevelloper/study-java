import { useEffect, useState } from 'react';
import { api } from './client';

export function useResource(path, enabled = true) {
    const [result, setResult] = useState(null);
    const [revision, setRevision] = useState(0);

    useEffect(() => {
        if (!enabled) return;
        const controller = new AbortController();
        api(path, { signal: controller.signal })
            .then((data) => setResult({ path, revision, data }))
            .catch((error) => {
                if (!controller.signal.aborted) setResult({ path, revision, error: error.message });
            });
        return () => controller.abort();
    }, [path, revision, enabled]);

    const current = result?.path === path ? result : null;
    return {
        data: current?.data,
        error: current?.error,
        loading: current?.revision !== revision,
        reload: () => setRevision((value) => value + 1),
    };
}
