import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SESSION_PREFIX = __ENV.SESSION_PREFIX || 'k6-local-session-';
const VUS = Number(__ENV.VUS || 1);
const MODE = __ENV.SEARCH_MODE || 'baseline';
const DURATION = __ENV.SEARCH_DURATION || '1m';
const SMOKE = __ENV.SMOKE === 'true';
const COMMON_QUERY = __ENV.SEARCH_COMMON_QUERY || '\uCD95\uC81C';
const RARE_QUERY = __ENV.SEARCH_RARE_QUERY || '\uBD88\uAF43';
const NO_RESULT_QUERY = __ENV.SEARCH_NO_RESULT_QUERY || '\uACB0\uACFC\uC5C6\uC74C';
const REGION_CODE = __ENV.SEARCH_REGION_CODE || '11';
const MONTH = __ENV.SEARCH_MONTH || new Date().toISOString().slice(0, 7);
const DEEP_PAGE = Number(__ENV.SEARCH_DEEP_PAGE || 50);

const supportedModes = new Set(['baseline', 'filter', 'deep', 'mixed', 'validation']);
if (!supportedModes.has(MODE)) {
  throw new Error(`SEARCH_MODE must be one of: ${Array.from(supportedModes).join(', ')}.`);
}
if (!Number.isInteger(VUS) || VUS < 1 || VUS > 10_000) {
  throw new Error('VUS must be an integer between 1 and 10000.');
}
if (![COMMON_QUERY, RARE_QUERY, NO_RESULT_QUERY].every((value) =>
  value && Array.from(value).length <= 10)) {
  throw new Error('Search terms must be present and at most 10 characters.');
}
if (!Number.isInteger(DEEP_PAGE) || DEEP_PAGE < 2) {
  throw new Error('SEARCH_DEEP_PAGE must be an integer greater than 1.');
}

export const options = {
  scenarios: {
    content_search: SMOKE ? {
      executor: 'constant-vus',
      vus: 1,
      duration: '10s',
      gracefulStop: '5s',
    } : {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      gracefulStop: '10s',
    },
  },
  thresholds: MODE === 'validation' ? {
    checks: ['rate>0.99'],
  } : {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

function sessionId() {
  return `${SESSION_PREFIX}${String(exec.vu.idInTest).padStart(3, '0')}`;
}

function encode(parameters) {
  return Object.entries(parameters)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&');
}

function search(session, searchCase, parameters, expectedStatus = 200) {
  const response = http.get(`${BASE_URL}/api/v1/contents?${encode(parameters)}`, {
    headers: { Cookie: `KGB_SESSION=${session}` },
    tags: {
      name: '/api/v1/contents',
      request: 'content_search',
      search_case: searchCase,
      search_mode: MODE,
      category: parameters.category || 'EVENT',
      page_depth: Number(parameters.page || 1) > 1 ? 'deep' : 'first',
    },
  });

  check(response, {
    [`${searchCase} returns ${expectedStatus}`]: (result) => result.status === expectedStatus,
  });
  if (response.status !== expectedStatus) {
    console.error(`SEARCH FAILED | case=${searchCase} | expected=${expectedStatus} | status=${response.status}`);
  }
}

function baseline(session) {
  const random = Math.random();
  if (random < 0.60) {
    search(session, 'common_result', { category: 'ALL', q: COMMON_QUERY, page: 1, size: 20 });
  } else if (random < 0.85) {
    search(session, 'rare_result', { category: 'ALL', q: RARE_QUERY, page: 1, size: 20 });
  } else {
    search(session, 'no_result', { category: 'ALL', q: NO_RESULT_QUERY, page: 1, size: 20 });
  }
}

function filter(session) {
  const cases = [
    ['all_region', { category: 'ALL', q: COMMON_QUERY, region_code: REGION_CODE }],
    ['place_region', { category: 'PLACE', q: COMMON_QUERY, region_code: REGION_CODE }],
    ['event_region', { category: 'EVENT', q: COMMON_QUERY, region_code: REGION_CODE }],
    ['event_month', { category: 'EVENT', q: COMMON_QUERY, month: MONTH }],
    ['event_region_month', {
      category: 'EVENT', q: COMMON_QUERY, region_code: REGION_CODE, month: MONTH,
    }],
  ];
  const [searchCase, parameters] = cases[Math.floor(Math.random() * cases.length)];
  search(session, searchCase, { ...parameters, page: 1, size: 20 });
}

function deep(session) {
  if (Math.random() < 0.5) {
    search(session, 'first_page', { category: 'ALL', q: COMMON_QUERY, page: 1, size: 20 });
  } else {
    search(session, 'deep_page', {
      category: 'ALL', q: COMMON_QUERY, page: DEEP_PAGE, size: 20,
    });
  }
}

function mixed(session) {
  if (Math.random() < 0.70) {
    filter(session);
    return;
  }
  const response = http.get(
    `${BASE_URL}/api/v1/map/contents?south=37.4&west=126.8&north=37.7&east=127.2&zoom=12`,
    {
      headers: { Cookie: `KGB_SESSION=${session}` },
      tags: {
        name: '/api/v1/map/contents',
        request: 'map_contents',
        search_mode: MODE,
      },
    },
  );
  check(response, { 'mixed map contents returns 200': (result) => result.status === 200 });
}

function validation(session) {
  const cases = [
    ['query_too_long', { category: 'ALL', q: '12345678901' }],
    ['query_required', { category: 'ALL' }],
    ['invalid_month', { category: 'EVENT', q: COMMON_QUERY, month: '2026-13' }],
    ['invalid_region', { category: 'ALL', q: COMMON_QUERY, region_code: 'invalid' }],
  ];
  const [searchCase, parameters] = cases[Math.floor(Math.random() * cases.length)];
  search(session, searchCase, { ...parameters, page: 1, size: 20 }, 400);
}

export default function () {
  const session = sessionId();
  ({ baseline, filter, deep, mixed, validation })[MODE](session);
  sleep(0.8 + Math.random() * 0.4);
}
