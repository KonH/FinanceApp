import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * The packaged renderer is a local file:// page that only talks to the main
 * process over IPC, so it gets a strict CSP. The dev server needs inline
 * scripts and a websocket for HMR, so the header is only added to builds.
 */
function productionCsp(): Plugin {
  return {
    name: 'financeapp-production-csp',
    apply: 'build',
    transformIndexHtml(html) {
      return html.replace(
        '<head>',
        `<head>\n    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'">`
      );
    }
  };
}

export default defineConfig({
  root: '.',
  // Relative asset URLs so the built page loads over file:// in the packaged app.
  base: './',
  plugins: [react(), productionCsp()],
  server: { port: 5173, strictPort: true },
  build: {
    outDir: 'dist/renderer',
    emptyOutDir: true,
    target: 'chrome120',
    sourcemap: true
  }
});
