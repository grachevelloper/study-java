import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const backend = new URL(process.env.STORE_API_TARGET || 'http://localhost:8080');
const proxy = {
    target: backend.origin,
    changeOrigin: true,
    configure(server) {
        server.on('proxyReq', (outgoing, incoming) => {
            // A same-origin request to Vite must remain same-origin after proxying to Spring.
            if (incoming.headers.origin === `http://${incoming.headers.host}`) {
                outgoing.setHeader('Origin', backend.origin);
            }
        });
    },
};

export default defineConfig({
    plugins: [react()],
    server: {
        host: 'localhost',
        strictPort: true,
        proxy: Object.fromEntries(
            ['/api', '/swagger-ui', '/v3/api-docs', '/actuator'].map((path) => [path, proxy]),
        ),
    },
});
