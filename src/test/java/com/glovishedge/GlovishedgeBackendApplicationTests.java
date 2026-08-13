package com.glovishedge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 실제 PostgreSQL 연결 + Flyway + Hibernate ddl-auto=validate를 검증하는 통합 테스트라
 * 로컬 DB 자격증명이 필요하다. src/main/resources/application-local.yml(gitignore 대상,
 * 각자 로컬 DB 정보로 채움)이 없으면 이 테스트는 여전히 실패한다 — 이는 환경 문제이지
 * 코드 문제가 아니다.
 */
@SpringBootTest
@ActiveProfiles("local")
class GlovishedgeBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
