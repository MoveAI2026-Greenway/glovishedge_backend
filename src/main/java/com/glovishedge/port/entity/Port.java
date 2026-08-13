package com.glovishedge.port.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ports")
public class Port {

    @Id
    @Column(name = "unlocode", length = 5, nullable = false)
    private String unlocode;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", length = 2, nullable = false, columnDefinition = "char(2)")
    private String countryCode;

    @Column(name = "lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal lng;

    @Column(name = "role", length = 11, nullable = false)
    private String role;

    @Column(name = "ets_applies", nullable = false)
    private boolean etsApplies;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Port() {
    }

    public Port(String unlocode, String name, String countryCode, BigDecimal lat, BigDecimal lng,
                String role, boolean etsApplies, Instant updatedAt) {
        this.unlocode = unlocode;
        this.name = name;
        this.countryCode = countryCode;
        this.lat = lat;
        this.lng = lng;
        this.role = role;
        this.etsApplies = etsApplies;
        this.updatedAt = updatedAt;
    }

    public String getUnlocode() {
        return unlocode;
    }

    public String getName() {
        return name;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public String getRole() {
        return role;
    }

    public boolean isEtsApplies() {
        return etsApplies;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
