const http = require('http');
const fs = require('fs');
const path = require('path');

const publicDir = path.join(__dirname, 'public');
const port = parseInt(process.env.PORT || '8080', 10);

const contentTypes = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon'
};

function resolveTargetPath(urlPath) {
  const normalizedPath = decodeURIComponent(urlPath.split('?')[0]);
  const requestedPath = normalizedPath === '/' ? '/index.html' : normalizedPath;
  const safePath = path.normalize(requestedPath).replace(/^([.][.][/\\])+/, '');
  const absolutePath = path.join(publicDir, safePath);

  if (!absolutePath.startsWith(publicDir)) {
    return path.join(publicDir, 'index.html');
  }

  return absolutePath;
}

const server = http.createServer((req, res) => {
  const targetPath = resolveTargetPath(req.url || '/');

  fs.stat(targetPath, (statErr, stats) => {
    if (!statErr && stats.isFile()) {
      const ext = path.extname(targetPath).toLowerCase();
      res.setHeader('Content-Type', contentTypes[ext] || 'application/octet-stream');
      fs.createReadStream(targetPath).pipe(res);
      return;
    }

    const fallback = path.join(publicDir, 'index.html');
    fs.createReadStream(fallback)
      .on('error', () => {
        res.statusCode = 500;
        res.end('No se encontró public/index.html.');
      })
      .pipe(res);
  });
});

server.listen(port, '0.0.0.0', () => {
  console.log(`Sellia App Hosting server escuchando en puerto ${port}`);
});
