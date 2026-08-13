'use strict';

const { seaRouteMulti, SnapFailedError, NoRouteError } = require('searoute-ts');

const VALID_PASSAGES = new Set([
  'suez', 'panama', 'gibraltar', 'babelmandeb', 'babalmandab', 'bosporus',
  'ormuz', 'malacca', 'sunda', 'dover', 'kiel', 'corinth', 'bering',
  'magellan', 'cape_horn', 'northwest', 'northeast',
]);

class InvalidRequestError extends Error {}

function isValidLngLat(point) {
  return (
    Array.isArray(point)
    && point.length === 2
    && typeof point[0] === 'number' && Number.isFinite(point[0])
    && typeof point[1] === 'number' && Number.isFinite(point[1])
    && point[0] >= -180 && point[0] <= 180
    && point[1] >= -90 && point[1] <= 90
  );
}

/**
 * 02_계산명세.md §7 / 08_데이터시드.md §5의 항로별 통과 금지·경유지 정의를 Spring 쪽에서
 * points/restrictions로 조립해 보낸다는 전제. 이 서비스는 그 조립 결과를 받아
 * searoute-ts 한 번의 계산(distance+path 동시 산출)만 수행한다 — 도메인 규칙(어떤 route가
 * 어떤 waypoint/restriction을 쓰는지)은 여기 두지 않는다.
 */
function computeRoute(body) {
  if (!body || typeof body !== 'object') {
    throw new InvalidRequestError('요청 본문이 없습니다');
  }

  const { points, restrictions } = body;

  if (!Array.isArray(points) || points.length < 2) {
    throw new InvalidRequestError('points는 좌표 2개 이상의 배열이어야 합니다 ([lng,lat] 쌍)');
  }
  for (const p of points) {
    if (!isValidLngLat(p)) {
      throw new InvalidRequestError(`유효하지 않은 좌표입니다: ${JSON.stringify(p)}`);
    }
  }

  let restrictionList = [];
  if (restrictions !== undefined) {
    if (!Array.isArray(restrictions)) {
      throw new InvalidRequestError('restrictions는 문자열 배열이어야 합니다');
    }
    for (const r of restrictions) {
      if (typeof r !== 'string' || !VALID_PASSAGES.has(r)) {
        throw new InvalidRequestError(`알 수 없는 restriction 값입니다: ${r}`);
      }
    }
    restrictionList = restrictions;
  }

  const feature = seaRouteMulti(points, {
    units: 'nauticalmiles',
    antimeridian: 'unwrap',
    restrictions: restrictionList,
  });

  // GeoJSON 좌표는 [lng, lat]. 03_API계약.md의 path 계약은 [[lat, lng], ...] 순서다.
  const path = feature.geometry.coordinates.map(([lng, lat]) => [lat, lng]);

  return {
    nm: feature.properties.length,
    path,
  };
}

module.exports = { computeRoute, InvalidRequestError, SnapFailedError, NoRouteError };
