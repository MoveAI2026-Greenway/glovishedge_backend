-- 08_데이터시드.md §2 — 고정 큐레이션 데이터(실시간 연동 아님). 갱신일 전부 2026-07-20.

INSERT INTO hazard_zones (zone_key, name, center_lat, center_lng, radius_km, level, message, updated_at) VALUES
(
    'red_sea', '홍해 (Red Sea)', 19, 40, 600, 'CRITICAL',
    '후티, 대사우디 해상봉쇄 선언(2026.7.20) · 이란-이스라엘-미국 무력 충돌로 호르무즈 대체 통로였던 홍해까지 위험 확산 · 사우디 미대응 상태로 시행 범위 불확실 · 수에즈 운하 경유 불가 권고',
    DATE '2026-07-20'
),
(
    'hormuz', '호르무즈 해협 (Strait of Hormuz)', 26.5, 56.5, 350, 'HIGH',
    '통항 제한 지속 중 · 전쟁 보험료 급등 · 이란 긴장 고조',
    DATE '2026-07-20'
),
(
    'malacca', '말라카 해협 (Strait of Malacca)', 3, 100.5, 220, 'MEDIUM',
    '소형 해적 활동 주의 · 야간 운항 시 경계 권고 · 일반 상업 운항 가능',
    DATE '2026-07-20'
),
(
    'cape', '희망봉 우회 (Cape of Good Hope)', -34.4, 18.5, 0, 'SAFE',
    '현재 경보 없음 · 홍해 분쟁 이후 주요 대체 항로 · 안전 운항 가능',
    DATE '2026-07-20'
);
