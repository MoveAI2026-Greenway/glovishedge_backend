package com.glovishedge.market.eua.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "eua_prices")
public class EuaPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "price_date", nullable = false, unique = true)
    private LocalDate priceDate;

    @Column(name = "price_eur", nullable = false, precision = 12, scale = 4)
    private BigDecimal priceEur;

    @Column(name = "source", length = 50, nullable = false)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected EuaPrice() {
    }

    public EuaPrice(LocalDate priceDate, BigDecimal priceEur, String source, Instant fetchedAt) {
        this.priceDate = priceDate;
        this.priceEur = priceEur;
        this.source = source;
        this.fetchedAt = fetchedAt;
    }

    /**
     * price_date UNIQUE 제약에 따른 upsert 용도 — 같은 거래일이 재조회되면 새 행을 만들지 않고 갱신한다.
     */
    public void updateSnapshot(BigDecimal priceEur, String source, Instant fetchedAt) {
        this.priceEur = priceEur;
        this.source = source;
        this.fetchedAt = fetchedAt;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public BigDecimal getPriceEur() {
        return priceEur;
    }

    public String getSource() {
        return source;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
