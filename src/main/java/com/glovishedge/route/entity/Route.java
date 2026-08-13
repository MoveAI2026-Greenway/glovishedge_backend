package com.glovishedge.route.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "routes")
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "route_key", length = 4, nullable = false, unique = true)
    private String routeKey;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "via_text", length = 100, nullable = false)
    private String viaText;

    @Column(name = "lane_note", length = 255, nullable = false)
    private String laneNote;

    @Column(name = "status", length = 10, nullable = false)
    private String status;

    @Column(name = "status_reason", nullable = false, columnDefinition = "text")
    private String statusReason;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

    @Column(name = "sea_leg_note", nullable = false, columnDefinition = "text")
    private String seaLegNote;

    @Column(name = "distance_nm", nullable = false)
    private int distanceNm;

    @Column(name = "base_other_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseOtherUsd;

    @Column(name = "war_risk_rate", nullable = false, precision = 6, scale = 5)
    private BigDecimal warRiskRate;

    @Column(name = "risk_level", length = 10, nullable = false)
    private String riskLevel;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Route() {
    }

    public Route(String routeKey, String name, String viaText, String laneNote, String status,
                 String statusReason, int leadTimeDays, String seaLegNote, int distanceNm,
                 BigDecimal baseOtherUsd, BigDecimal warRiskRate, String riskLevel, Instant updatedAt) {
        this.routeKey = routeKey;
        this.name = name;
        this.viaText = viaText;
        this.laneNote = laneNote;
        this.status = status;
        this.statusReason = statusReason;
        this.leadTimeDays = leadTimeDays;
        this.seaLegNote = seaLegNote;
        this.distanceNm = distanceNm;
        this.baseOtherUsd = baseOtherUsd;
        this.warRiskRate = warRiskRate;
        this.riskLevel = riskLevel;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getRouteKey() {
        return routeKey;
    }

    public String getName() {
        return name;
    }

    public String getViaText() {
        return viaText;
    }

    public String getLaneNote() {
        return laneNote;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public int getLeadTimeDays() {
        return leadTimeDays;
    }

    public String getSeaLegNote() {
        return seaLegNote;
    }

    public int getDistanceNm() {
        return distanceNm;
    }

    public BigDecimal getBaseOtherUsd() {
        return baseOtherUsd;
    }

    public BigDecimal getWarRiskRate() {
        return warRiskRate;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
