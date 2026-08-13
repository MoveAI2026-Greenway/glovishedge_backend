package com.glovishedge.market.fx.repository;

import com.glovishedge.market.fx.entity.FxRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    Optional<FxRate> findFirstByBaseCurrencyAndQuoteCurrencyOrderByFetchedAtDesc(
            String baseCurrency, String quoteCurrency);
}
