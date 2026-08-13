-- 08_데이터시드.md §6 — 출발 18 + 도착 32 = 50개.
-- EU ETS 적용 여부는 도착 국가 기준. GB(영국)는 EU ETS 미적용, NO(노르웨이)는 EEA로 적용.

INSERT INTO ports (unlocode, name, country_code, lat, lng, role, ets_applies) VALUES
-- 출발 — KR 대한민국
('KRPUS', '부산항', 'KR', 35.1, 129.04, 'origin', false),
('KRINC', '인천항', 'KR', 37.45, 126.6, 'origin', false),
('KRKAN', '광양항', 'KR', 34.91, 127.75, 'origin', false),
('KRUSN', '울산항', 'KR', 35.5, 129.39, 'origin', false),
('KRPTK', '평택·당진항', 'KR', 36.97, 126.82, 'origin', false),
('KRMAS', '마산항', 'KR', 35.2, 128.58, 'origin', false),
-- 출발 — CN 중국
('CNSHA', '상하이항', 'CN', 31.23, 121.49, 'origin', false),
('CNNGB', '닝보항', 'CN', 29.87, 121.55, 'origin', false),
('CNSZX', '선전항', 'CN', 22.54, 113.93, 'origin', false),
('CNTAO', '칭다오항', 'CN', 36.07, 120.38, 'origin', false),
('CNNSA', '광저우(난사)항', 'CN', 22.6, 113.6, 'origin', false),
('CNTXG', '톈진항', 'CN', 38.98, 117.7, 'origin', false),
-- 출발 — JP 일본
('JPTYO', '도쿄항', 'JP', 35.65, 139.77, 'origin', false),
('JPYOK', '요코하마항', 'JP', 35.44, 139.64, 'origin', false),
('JPOSA', '오사카항', 'JP', 34.65, 135.43, 'origin', false),
('JPUKB', '고베항', 'JP', 34.69, 135.18, 'origin', false),
('JPNGO', '나고야항', 'JP', 35.08, 136.88, 'origin', false),
-- 출발 — SG 싱가포르
('SGSIN', '싱가포르항', 'SG', 1.29, 103.85, 'origin', false),
-- 도착 — DE 독일 (EU ETS 적용)
('DEHAM', '함부르크항', 'DE', 53.55, 10, 'destination', true),
('DEBRE', '브레멘항', 'DE', 53.08, 8.8, 'destination', true),
('DEBRV', '브레머하펜항', 'DE', 53.54, 8.58, 'destination', true),
-- 도착 — NL 네덜란드 (EU ETS 적용)
('NLRTM', '로테르담항', 'NL', 51.92, 4.48, 'destination', true),
('NLAMS', '암스테르담항', 'NL', 52.37, 4.9, 'destination', true),
-- 도착 — BE 벨기에 (EU ETS 적용)
('BEANR', '앤트워프항', 'BE', 51.22, 4.4, 'destination', true),
('BEZEE', '제브뤼헤항', 'BE', 51.33, 3.2, 'destination', true),
-- 도착 — FR 프랑스 (EU ETS 적용)
('FRLEH', '르아브르항', 'FR', 49.49, 0.11, 'destination', true),
('FRMRS', '마르세유항', 'FR', 43.3, 5.37, 'destination', true),
-- 도착 — ES 스페인 (EU ETS 적용)
('ESVLC', '발렌시아항', 'ES', 39.45, -0.32, 'destination', true),
('ESALG', '알헤시라스항', 'ES', 36.13, -5.45, 'destination', true),
-- 도착 — IT 이탈리아 (EU ETS 적용)
('ITGOA', '제노바항', 'IT', 44.41, 8.93, 'destination', true),
('ITSPE', '라스페치아항', 'IT', 44.1, 9.82, 'destination', true),
-- 도착 — GR 그리스 (EU ETS 적용)
('GRPIR', '피레우스항', 'GR', 37.94, 23.63, 'destination', true),
-- 도착 — PL 폴란드 (EU ETS 적용)
('PLGDN', '그단스크항', 'PL', 54.35, 18.65, 'destination', true),
-- 도착 — SE 스웨덴 (EU ETS 적용)
('SEGOT', '예테보리항', 'SE', 57.7, 11.97, 'destination', true),
-- 도착 — PT 포르투갈 (EU ETS 적용)
('PTSIE', '시네스항', 'PT', 37.95, -8.87, 'destination', true),
-- 도착 — DK 덴마크 (EU ETS 적용)
('DKAAR', '오르후스항', 'DK', 56.15, 10.22, 'destination', true),
-- 도착 — FI 핀란드 (EU ETS 적용)
('FIHEL', '헬싱키항', 'FI', 60.17, 24.95, 'destination', true),
-- 도착 — IE 아일랜드 (EU ETS 적용)
('IEDUB', '더블린항', 'IE', 53.35, -6.22, 'destination', true),
-- 도착 — HR 크로아티아 (EU ETS 적용)
('HRRJK', '리예카항', 'HR', 45.33, 14.44, 'destination', true),
-- 도착 — SI 슬로베니아 (EU ETS 적용)
('SIKOP', '코페르항', 'SI', 45.55, 13.73, 'destination', true),
-- 도착 — MT 몰타 (EU ETS 적용)
('MTMAR', '마르사슬록항', 'MT', 35.84, 14.54, 'destination', true),
-- 도착 — CY 키프로스 (EU ETS 적용)
('CYLMS', '리마솔항', 'CY', 34.65, 33.04, 'destination', true),
-- 도착 — RO 루마니아 (EU ETS 적용)
('ROCND', '콘스탄차항', 'RO', 44.17, 28.65, 'destination', true),
-- 도착 — BG 불가리아 (EU ETS 적용)
('BGVAR', '바르나항', 'BG', 43.2, 27.92, 'destination', true),
-- 도착 — LV 라트비아 (EU ETS 적용)
('LVRIX', '리가항', 'LV', 56.95, 24.1, 'destination', true),
-- 도착 — LT 리투아니아 (EU ETS 적용)
('LTKLJ', '클라이페다항', 'LT', 55.71, 21.14, 'destination', true),
-- 도착 — EE 에스토니아 (EU ETS 적용)
('EETLL', '탈린항', 'EE', 59.44, 24.75, 'destination', true),
-- 도착 — NO 노르웨이 (EEA, EU ETS 적용)
('NOOSL', '오슬로항', 'NO', 59.91, 10.75, 'destination', true),
-- 도착 — GB 영국 (EU ETS 미적용)
('GBFXT', '펠릭스토우항', 'GB', 51.96, 1.35, 'destination', false),
('GBLON', '런던항', 'GB', 51.5, 0.05, 'destination', false);
