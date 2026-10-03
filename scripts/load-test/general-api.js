import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SESSION = __ENV.SESSION;
const MAX_VUS = Number(__ENV.VUS || 40);

if (!SESSION) {
  throw new Error('SESSION 환경변수에 유효한 로컬 KGB_SESSION 원문을 입력해야 합니다.');
}

if (!Number.isInteger(MAX_VUS) || MAX_VUS < 1 || MAX_VUS > 999) {
  throw new Error('VUS는 1 이상 999 이하의 정수여야 합니다.');
}

const INITIAL_VUS = Math.max(1, Math.ceil(MAX_VUS / 10));
const QUARTER_VUS = Math.max(1, Math.ceil(MAX_VUS / 4));
const HALF_VUS = Math.max(1, Math.ceil(MAX_VUS / 2));

export const options = {
  scenarios: {
    general_api: {
      executor: 'ramping-vus',
      startVUs: INITIAL_VUS,
      stages: [
        { target: INITIAL_VUS, duration: '2m' },
        { target: QUARTER_VUS, duration: '10s' },
        { target: QUARTER_VUS, duration: '2m' },
        { target: HALF_VUS, duration: '10s' },
        { target: HALF_VUS, duration: '2m' },
        { target: MAX_VUS, duration: '10s' },
        { target: MAX_VUS, duration: '2m' },
        { target: 0, duration: '10s' },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const params = {
  headers: {
    Cookie: `KGB_SESSION=${SESSION}`,
  },
};

const requests = [
  {
    limit: 0.30,
    name: 'member_me',
    path: '/api/v1/members/me',
  },
  {
    limit: 0.55,
    name: 'map_contents',
    path: '/api/v1/map/contents?south=37.4&west=126.8&north=37.7&east=127.2&zoom=12',
  },
  {
    limit: 0.70,
    name: 'guidebook_list',
    path: '/api/v1/guidebooks?size=20',
  },
  {
    limit: 0.80,
    name: 'preference_options',
    path: '/api/v1/preference-options',
  },
  {
    limit: 0.90,
    name: 'member_preferences',
    path: '/api/v1/members/me/preferences',
  },
  {
    limit: 0.95,
    name: 'notifications',
    path: '/api/v1/notifications?page=1&size=4',
  },
  {
    limit: 1,
    name: 'credit_wallet',
    path: '/api/v1/credits/wallet',
  },
];

export default function () {
  const random = Math.random();
  const request = requests.find((candidate) => random < candidate.limit);
  const response = http.get(`${BASE_URL}${request.path}`, {
    ...params,
    tags: { request: request.name },
  });

  check(response, {
    [`${request.name} returns 200`]: (result) => result.status === 200,
  });

  sleep(1 + Math.random() * 2);
}
