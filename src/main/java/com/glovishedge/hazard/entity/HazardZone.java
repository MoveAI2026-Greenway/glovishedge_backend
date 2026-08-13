package com.glovishedge.hazard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "hazard_zones")
public class HazardZone {

    @Id
    @Column(name = "zone_key", length = 20, nullable = false)
    private String zoneKey;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "center_lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal centerLat;

    @Column(name = "center_lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal centerLng;

    @Column(name = "radius_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal radiusKm;

    @Column(name = "level", length = 10, nullable = false)
    private String level;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "updated_at", nullable = false)
    private LocalDate updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    protected HazardZone() {
    }

    public HazardZone(String zoneKey, String name, BigDecimal centerLat, BigDecimal centerLng,
                       BigDecimal radiusKm, String level, String message, LocalDate updatedAt,
                       String updatedBy) {
        this.zoneKey = zoneKey;
        this.name = name;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
        this.radiusKm = radiusKm;
        this.level = level;
        this.message = message;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public String getZoneKey() {
        return zoneKey;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getCenterLat() {
        return centerLat;
    }

    public BigDecimal getCenterLng() {
        return centerLng;
    }

    public BigDecimal getRadiusKm() {
        return radiusKm;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public LocalDate getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
