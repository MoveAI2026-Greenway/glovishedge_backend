'use strict';

const express = require('express');
const { computeRoute, InvalidRequestError, SnapFailedError, NoRouteError } = require('./route');

const app = express();
app.use(express.json({ limit: '256kb' }));

const PORT = process.env.PORT || 3001;

// 헬스체크 — Spring이 사용하지 않아도 운영상 최소한으로 둔다.
app.get('/health', (_req, res) => {
  res.json({ status: 'ok' });
});

// 내부 전용 endpoint. 외부(Browser)에는 절대 노출하지 않는다 — Spring의 GET /api/sea-distance만 공개 API다.
app.post('/route', (req, res) => {
  try {
    const result = computeRoute(req.body);
    res.json(result);
  } catch (err) {
    if (err instanceof InvalidRequestError) {
      res.status(400).json({ error: err.message });
      return;
    }
    if (err instanceof SnapFailedError) {
      res.status(400).json({ error: '좌표가 해상 항로망에서 너무 멀리 떨어져 있습니다' });
      return;
    }
    if (err instanceof NoRouteError) {
      res.status(422).json({ error: '주어진 restrictions로는 경로를 찾을 수 없습니다' });
      return;
    }
    // eslint-disable-next-line no-console
    console.error('route computation failed:', err.message);
    res.status(500).json({ error: '항로 계산 중 오류가 발생했습니다' });
  }
});

if (require.main === module) {
  app.listen(PORT, () => {
    // eslint-disable-next-line no-console
    console.log(`searoute-service listening on port ${PORT}`);
  });
}

module.exports = app;
