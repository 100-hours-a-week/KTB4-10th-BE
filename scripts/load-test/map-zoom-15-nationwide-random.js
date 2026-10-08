import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SESSION_PREFIX = __ENV.SESSION_PREFIX || 'k6-local-session-';
const MAX_VUS = Number(__ENV.VUS || 500);
const CACHE_MODE = __ENV.CACHE_MODE || 'cache_on_tile_005';

if (!Number.isInteger(MAX_VUS) || MAX_VUS < 1 || MAX_VUS > 2000) {
  throw new Error('VUS는 1 이상 2000 이하의 정수여야 합니다.');
}

const INITIAL_VUS = Math.max(1, Math.ceil(MAX_VUS / 10));
const QUARTER_VUS = Math.max(1, Math.ceil(MAX_VUS / 4));
const HALF_VUS = Math.max(1, Math.ceil(MAX_VUS / 2));
const VIEWPORT_LATITUDE_SPAN = 0.0300;
const VIEWPORT_LONGITUDE_SPAN = 0.0600;

// 전국에서 서로 다른 bounds를 생성한다. 바다 비중을 줄이기 위해
// 한반도 본토와 제주 범위를 분리하되, 특정 관광지를 재사용하지 않는다.
const RANDOM_AREAS = [
  { name: 'mainland', weight: 0.9, south: 34.3, west: 126.0, north: 37.9, east: 129.5 },
  { name: 'jeju', weight: 0.1, south: 33.2, west: 126.1, north: 33.6, east: 126.9 },
];

function randomBetween(min, max) {
  return min + Math.random() * (max - min);
}

function randomViewport() {
  const pick = Math.random();
  let accumulatedWeight = 0;
  let area = RANDOM_AREAS[RANDOM_AREAS.length - 1];
  for (const candidate of RANDOM_AREAS) {
    accumulatedWeight += candidate.weight;
    if (pick < accumulatedWeight) {
      area = candidate;
      break;
    }
  }

  const halfLatitude = VIEWPORT_LATITUDE_SPAN / 2;
  const halfLongitude = VIEWPORT_LONGITUDE_SPAN / 2;
  const centerLatitude = randomBetween(area.south + halfLatitude, area.north - halfLatitude);
  const centerLongitude = randomBetween(area.west + halfLongitude, area.east - halfLongitude);

  return {
    area: area.name,
    south: (centerLatitude - halfLatitude).toFixed(5),
    west: (centerLongitude - halfLongitude).toFixed(5),
    north: (centerLatitude + halfLatitude).toFixed(5),
    east: (centerLongitude + halfLongitude).toFixed(5),
  };
}

export const options = {
  scenarios: {
    map_zoom_15_nationwide_random: {
      executor: 'ramping-vus',
      startVUs: INITIAL_VUS,
      stages: [
        { target: INITIAL_VUS, duration: '5s' },
        { target: QUARTER_VUS, duration: '5s' },
        { target: HALF_VUS, duration: '5s' },
        { target: MAX_VUS, duration: '10s' },
        { target: MAX_VUS, duration: '30s' },
        { target: 0, duration: '5s' },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export default function () {
  const session = `${SESSION_PREFIX}${String(__VU).padStart(3, '0')}`;
  const viewport = randomViewport();
  const query = [
    `south=${viewport.south}`,
    `west=${viewport.west}`,
    `north=${viewport.north}`,
    `east=${viewport.east}`,
    'zoom=15',
  ].join('&');
  const response = http.get(`${BASE_URL}/api/v1/map/contents?${query}`, {
    headers: { Cookie: `KGB_SESSION=${session}` },
    tags: {
      name: '/api/v1/map/contents',
      request: 'map_zoom_15_nationwide_random',
      region: viewport.area,
      map_mode: 'detail',
      zoom: '15',
      query_version: `zoom_15_nationwide_random_${CACHE_MODE}`,
    },
  });

  check(response, {
    'map zoom 15 nationwide random returns 200': (result) => result.status === 200,
  });

  if (response.status !== 200) {
    console.error(`MAP NATIONWIDE RANDOM FAILED | status=${response.status}`);
  }
  sleep(1);
}
