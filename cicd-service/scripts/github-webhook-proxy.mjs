// Local pilot: expose only signed webhooks, never the file-upload API.
import http from 'node:http';

http.createServer((request, response) => {
  if (request.method !== 'POST' || !['/api/v1/webhooks/github', '/api/v1/webhooks/sonar'].includes(request.url)) {
    response.writeHead(404).end();
    return;
  }
  const upstream = http.request({
    hostname: '127.0.0.1', port: 8082, path: request.url, method: 'POST',
    headers: { ...request.headers, host: 'localhost:8082' },
  }, (result) => {
    response.writeHead(result.statusCode, result.headers);
    result.pipe(response);
  });
  upstream.setTimeout(15000, () => upstream.destroy());
  upstream.on('error', () => {
    if (!response.headersSent) response.writeHead(502);
    response.end();
  });
  request.on('aborted', () => upstream.destroy());
  request.pipe(upstream);
}).listen(8095, '127.0.0.1', () => {
  console.log('GitHub/Sonar webhook-only proxy listening on http://127.0.0.1:8095');
});
