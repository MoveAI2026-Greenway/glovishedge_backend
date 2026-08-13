package com.glovishedge.port.repository;

import com.glovishedge.port.entity.Port;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortRepository extends JpaRepository<Port, String> {
}
