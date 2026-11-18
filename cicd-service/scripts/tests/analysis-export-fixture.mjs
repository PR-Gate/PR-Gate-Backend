// Isolated integration-test fixture. Never point a real deployment at this server.
import { createServer } from 'node:http';
createServer((request, response) => {
  const url = new URL(request.url, 'http://fixture');
  let body;
  if (url.pathname === '/api/ce/task') {
    body = { task: { status: 'SUCCESS', componentKey: 'example-backend', analysisId: 'analysis-123' } };
  } else if (url.pathname === '/api/project_analyses/search') {
    body = { analyses: [{ key: 'analysis-123', revision: 'b'.repeat(40) }] };
  } else if (url.pathname === '/api/qualitygates/project_status' && url.searchParams.get('analysisId') === 'analysis-123') {
    body = { projectStatus: { status: 'ERROR' } };
  } else {
    response.writeHead(404).end();
    return;
  }
  response.writeHead(200, { 'Content-Type': 'application/json' });
  response.end(JSON.stringify(body));
}).listen(9000, '0.0.0.0');
