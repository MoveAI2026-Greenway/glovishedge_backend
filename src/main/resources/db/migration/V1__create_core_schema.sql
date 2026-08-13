-- Core master data schema.
-- ports / routes / hazard_zones = curated master data (Flyway seed).
-- fx_rates / eua_prices = external market data cache (populated at runtime, not seeded here).

CREATE TABLE ports (
    unlocode      VARCHAR(5)      NOT NULL,
    name          VARCHAR(100)    NOT NULL,
    country_code  CHAR(2)         NOT NULL,
    lat           NUMERIC(9, 6)   NOT NULL,
    lng           NUMERIC(9, 6)   NOT NULL,
    role          VARCHAR(11)     NOT NULL,
    ets_applies   BOOLEAN         NOT NULL,
    updated_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT pk_ports PRIMARY KEY (unlocode),
    CONSTRAINT ck_ports_role CHECK (role IN ('origin', 'destination'))
);

CREATE TABLE routes (
    id               BIGSERIAL       NOT NULL,
    route_key        VARCHAR(4)      NOT NULL,
    name             VARCHAR(100)    NOT NULL,
    via_text         VARCHAR(100)    NOT NULL,
    lane_note        VARCHAR(255)    NOT NULL,
    status           VARCHAR(10)     NOT NULL,
    status_reason    TEXT            NOT NULL,
    lead_time_days   INTEGER         NOT NULL,
    sea_leg_note     TEXT            NOT NULL,
    distance_nm      INTEGER         NOT NULL,
    base_other_usd   NUMERIC(12, 2)  NOT NULL,
    war_risk_rate    NUMERIC(6, 5)   NOT NULL,
    risk_level       VARCHAR(10)     NOT NULL,
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT pk_routes PRIMARY KEY (id),
    CONSTRAINT uq_routes_route_key UNIQUE (route_key),
    CONSTRAINT ck_routes_status CHECK (status IN ('best', 'limited', 'blocked')),
    CONSTRAINT ck_routes_risk_level CHECK (risk_level IN ('안전', '주의', '위험'))
);

CREATE TABLE hazard_zones (
    zone_key     VARCHAR(20)     NOT NULL,
    name         VARCHAR(100)    NOT NULL,
    center_lat   NUMERIC(9, 6)   NOT NULL,
    center_lng   NUMERIC(9, 6)   NOT NULL,
    radius_km    NUMERIC(8, 2)   NOT NULL,
    level        VARCHAR(10)     NOT NULL,
    message      TEXT            NOT NULL,
    updated_at   DATE            NOT NULL,
    updated_by   VARCHAR(100),
    CONSTRAINT pk_hazard_zones PRIMARY KEY (zone_key),
    CONSTRAINT ck_hazard_zones_level CHECK (level IN ('CRITICAL', 'HIGH', 'MEDIUM', 'SAFE'))
);

CREATE TABLE fx_rates (
    id              BIGSERIAL       NOT NULL,
    base_currency   VARCHAR(3)      NOT NULL,
    quote_currency  VARCHAR(3)      NOT NULL,
    rate            NUMERIC(18, 8)  NOT NULL,
    source          VARCHAR(50)     NOT NULL,
    fetched_at      TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_fx_rates PRIMARY KEY (id)
);

CREATE INDEX idx_fx_rates_lookup ON fx_rates (base_currency, quote_currency, fetched_at DESC);

CREATE TABLE eua_prices (
    id           BIGSERIAL       NOT NULL,
    price_date   DATE            NOT NULL,
    price_eur    NUMERIC(12, 4)  NOT NULL,
    source       VARCHAR(50)     NOT NULL,
    fetched_at   TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_eua_prices PRIMARY KEY (id),
    CONSTRAINT uq_eua_prices_price_date UNIQUE (price_date)
);
