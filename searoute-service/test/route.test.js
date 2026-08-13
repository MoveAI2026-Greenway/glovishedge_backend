'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { computeRoute, InvalidRequestError } = require('../src/route');

const BUSAN = [129.04, 35.1];
const HAMBURG = [10, 53.55];
const SINGAPORE = [103.85, 1.28];
const COLOMBO = [79.85, 6.9];

test('valid two-point route returns nm > 0 and a non-empty path', () => {
  const result = computeRoute({ points: [BUSAN, HAMBURG] });
  assert.ok(result.nm > 0);
  assert.ok(Array.isArray(result.path));
  assert.ok(result.path.length > 0);
});

test('path coordinates are [lat, lng], not GeoJSON [lng, lat]', () => {
  const result = computeRoute({ points: [BUSAN, HAMBURG] });
  const [firstLat, firstLng] = result.path[0];
  // Busan 근해에서 시작해야 한다: lat ~35, lng ~129 (반대로 나오면 순서가 틀린 것)
  assert.ok(firstLat > 20 && firstLat < 45, `lat 범위 이상: ${firstLat}`);
  assert.ok(firstLng > 100 && firstLng < 140, `lng 범위 이상: ${firstLng}`);
});

test('golden validation: A/B (direct or via Singapore) ~= 11259 nm', () => {
  const direct = computeRoute({ points: [BUSAN, HAMBURG] });
  assert.ok(Math.abs(direct.nm - 11259) < 5, `실제값 ${direct.nm}`);

  const viaSingapore = computeRoute({ points: [BUSAN, SINGAPORE, HAMBURG] });
  assert.ok(Math.abs(viaSingapore.nm - 11259) < 5, `실제값 ${viaSingapore.nm}`);
});

test('golden validation: D (via Singapore + Colombo) ~= 11317 nm', () => {
  const result = computeRoute({ points: [BUSAN, SINGAPORE, COLOMBO, HAMBURG] });
  assert.ok(Math.abs(result.nm - 11317) < 5, `실제값 ${result.nm}`);
});

test('golden validation: C (Cape, suez+babelmandeb+panama blocked) ~= 14549 nm', () => {
  const result = computeRoute({
    points: [BUSAN, HAMBURG],
    restrictions: ['suez', 'babelmandeb', 'panama'],
  });
  assert.ok(Math.abs(result.nm - 14549) < 5, `실제값 ${result.nm}`);
});

test('Cape route actually detours via southern Africa, not Panama', () => {
  const result = computeRoute({
    points: [BUSAN, HAMBURG],
    restrictions: ['suez', 'babelmandeb', 'panama'],
  });
  // 남아프리카 희망봉 부근(약 lat -35, lng 18)을 지나야 한다 — bbox의 최저 위도로 확인
  const minLat = Math.min(...result.path.map((p) => p[0]));
  assert.ok(minLat < -20, `남반구를 지나지 않음(minLat=${minLat}) — 파나마로 샜을 가능성`);
});

test('invalid coordinate is rejected', () => {
  assert.throws(
    () => computeRoute({ points: [[999, 999], HAMBURG] }),
    InvalidRequestError,
  );
});

test('missing points is rejected', () => {
  assert.throws(() => computeRoute({}), InvalidRequestError);
});

test('single point is rejected (need at least 2)', () => {
  assert.throws(() => computeRoute({ points: [BUSAN] }), InvalidRequestError);
});

test('unknown restriction value is rejected', () => {
  assert.throws(
    () => computeRoute({ points: [BUSAN, HAMBURG], restrictions: ['not-a-real-passage'] }),
    InvalidRequestError,
  );
});
