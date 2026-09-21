import { createServer } from 'node:http';

const port = Number(process.env.CERTMUSE_AI_STUB_PORT ?? 9090);

const server = createServer(async (request, response) => {
  const mode = request.headers['x-certmuse-e2e-mode'] ?? 'success';
  if (request.method !== 'POST' || !request.url?.endsWith('/chat/completions')) {
    response.writeHead(404, { 'content-type': 'application/json' });
    response.end(JSON.stringify({ error: { code: 'not_found' } }));
    return;
  }
  if (mode === 'timeout') {
    await new Promise(resolve => setTimeout(resolve, 35_000));
  }
  if (mode === 'rate-limit') {
    response.writeHead(429, { 'content-type': 'application/json', 'retry-after': '1' });
    response.end(JSON.stringify({ error: { code: 'rate_limit', message: 'e2e injected rate limit' } }));
    return;
  }
  if (mode === 'unavailable') {
    response.writeHead(503, { 'content-type': 'application/json' });
    response.end(JSON.stringify({ error: { code: 'unavailable', message: 'e2e injected outage' } }));
    return;
  }
  response.writeHead(200, { 'content-type': 'application/json' });
  response.end(JSON.stringify({
    id: 'e2e-stub-response',
    object: 'chat.completion',
    choices: [{ index: 0, message: { role: 'assistant', content: 'e2e stub response' }, finish_reason: 'stop' }]
  }));
});

server.listen(port, '0.0.0.0', () => console.log(`CertMuse AI e2e stub listening on ${port}`));
