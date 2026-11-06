// Прокси не содержит ключей: локальный backend запускается только с явным профилем dev.
module.exports = {
  '/api/**': {
    target: process.env['BACKEND_URL'] || 'http://127.0.0.1:8080',
    secure: false,
    changeOrigin: true,
  },
};
