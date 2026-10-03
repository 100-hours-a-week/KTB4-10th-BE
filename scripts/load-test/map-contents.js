import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SESSION_PREFIX = __ENV.SESSION_PREFIX || 'k6-local-session-';
const MAX_VUS = Number(__ENV.VUS || 40);
const SMOKE = __ENV.SMOKE === 'true';

if (!Number.isInteger(MAX_VUS) || MAX_VUS < 1 || MAX_VUS > 999) {
  throw new Error('VUS는 1 이상 999 이하의 정수여야 합니다.');
}

const INITIAL_VUS = Math.max(1, Math.ceil(MAX_VUS / 10));
const QUARTER_VUS = Math.max(1, Math.ceil(MAX_VUS / 4));
const HALF_VUS = Math.max(1, Math.ceil(MAX_VUS / 2));

const viewports = [
  { region: 'seoul', mode: 'cluster', south: 37.40, west: 126.80, north: 37.70, east: 127.20, zoom: 12 },
  { region: 'seoul', mode: 'detail', south: 37.54, west: 126.96, north: 37.58, east: 127.02, zoom: 16 },
  { region: 'busan', mode: 'cluster', south: 35.00, west: 128.90, north: 35.30, east: 129.30, zoom: 12 },
  { region: 'busan', mode: 'detail', south: 35.14, west: 129.02, north: 35.20, east: 129.10, zoom: 16 },
  { region: 'jeju', mode: 'cluster', south: 33.20, west: 126.10, north: 33.60, east: 126.90, zoom: 12 },
  { region: 'jeju', mode: 'detail', south: 33.47, west: 126.48, north: 33.53, east: 126.58, zoom: 16 },
  { region: 'daegu', mode: 'cluster', south: 35.75, west: 128.45, north: 36.00, east: 128.75, zoom: 12 },
  { region: 'daegu', mode: 'detail', south: 35.84, west: 128.56, north: 35.90, east: 128.66, zoom: 16 },
  { region: 'daejeon', mode: 'cluster', south: 36.20, west: 127.25, north: 36.50, east: 127.55, zoom: 12 },
  { region: 'daejeon', mode: 'detail', south: 36.32, west: 127.37, north: 36.38, east: 127.47, zoom: 16 },
  { region: 'incheon', mode: 'cluster', south: 37.30, west: 126.50, north: 37.60, east: 126.80, zoom: 12 },
  { region: 'incheon', mode: 'detail', south: 37.43, west: 126.65, north: 37.49, east: 126.75, zoom: 16 },
];

export const options = {
  scenarios: {
    map_contents: {
      executor: 'ramping-vus',
      startVUs: INITIAL_VUS,
      stages: SMOKE ? [{ target: 1, duration: '5s' }] : [
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
    'http_req_duration{map_mode:cluster}': ['p(95)<1000'],
    'http_req_duration{map_mode:detail}': ['p(95)<1000'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export default function () {
  const session = `${SESSION_PREFIX}${String(exec.vu.idInTest).padStart(3, '0')}`;
  const viewportIndex = (exec.vu.idInTest + exec.scenario.iterationInTest) % viewports.length;
  const viewport = viewports[viewportIndex];
  const query = [
    `south=${viewport.south}`,
    `west=${viewport.west}`,
    `north=${viewport.north}`,
    `east=${viewport.east}`,
    `zoom=${viewport.zoom}`,
  ].join('&');

  const response = http.get(`${BASE_URL}/api/v1/map/contents?${query}`, {
    headers: {
      Cookie: `KGB_SESSION=${session}`,
    },
    tags: {
      name: '/api/v1/map/contents',
      request: 'map_contents',
      region: viewport.region,
      map_mode: viewport.mode,
      zoom: String(viewport.zoom),
    },
  });

  check(response, {
    'map_contents returns 200': (result) => result.status === 200,
  });

  sleep(1 + Math.random() * 2);
}
