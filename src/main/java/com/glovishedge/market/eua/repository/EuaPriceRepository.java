package com.glovishedge.market.eua.repository;

import com.glovishedge.market.eua.entity.EuaPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EuaPriceRepository extends JpaRepository<EuaPrice, Long> {

    Optional<EuaPrice> findByPriceDate(LocalDate priceDate);

    Optional<EuaPrice> findFirstByOrderByPriceDateDesc();

    /**
     * dayChangePct + recentCloses(PROVISIONAL: 최근 5 거래일) 계산용 — eua_prices에는 등락률/전일종가
     * 컬럼이 없어 최근 거래일 행에서 구한다. 최소 2개가 있어야 등락률을 계산할 수 있다.
     */
    List<EuaPrice> findTop5ByOrderByPriceDateDesc();
}
