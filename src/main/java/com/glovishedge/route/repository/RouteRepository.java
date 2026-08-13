package com.glovishedge.route.repository;

import com.glovishedge.route.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {

    Optional<Route> findByRouteKey(String routeKey);
}
