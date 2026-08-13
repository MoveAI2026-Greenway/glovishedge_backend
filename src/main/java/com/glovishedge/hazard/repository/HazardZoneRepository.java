package com.glovishedge.hazard.repository;

import com.glovishedge.hazard.entity.HazardZone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HazardZoneRepository extends JpaRepository<HazardZone, String> {
}
