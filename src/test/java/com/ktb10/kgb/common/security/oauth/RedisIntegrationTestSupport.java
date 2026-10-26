package com.ktb10.kgb.common.security.oauth;

import com.ktb10.kgb.support.RedisSessionTestSupport;

/** OAuth와 서비스 세션 테스트가 동일한 격리 Redis를 공유합니다. */
abstract class RedisIntegrationTestSupport extends RedisSessionTestSupport {
}
