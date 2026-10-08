import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

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
const REGION_CHANGE_RATE = 0.10;
const MAX_RANDOM_CENTER_OFFSET = 0.0002;

// 로컬 tourism_contents의 실제 0.1도 격자 분포에서 콘텐츠가 존재하는 지역을
// 전국 권역별로 골랐다. 특정 수도권 핫스팟에만 트래픽이 집중되지 않도록 동일 비율로 배분한다.
const TOURISM_HUBS = [
  { name: 'seoul_west', latitude: 37.55, longitude: 126.95 },
  { name: 'seoul_east', latitude: 37.55, longitude: 127.05 },
  { name: 'incheon', latitude: 37.45, longitude: 126.65 },
  { name: 'suwon', latitude: 37.25, longitude: 127.05 },
  { name: 'goyang', latitude: 37.65, longitude: 126.75 },
  { name: 'paju', latitude: 37.75, longitude: 126.65 },
  { name: 'chuncheon', latitude: 37.85, longitude: 127.75 },
  { name: 'gangneung', latitude: 37.75, longitude: 128.95 },
  { name: 'sokcho', latitude: 38.25, longitude: 128.55 },
  { name: 'daejeon', latitude: 36.35, longitude: 127.35 },
  { name: 'cheongju', latitude: 36.65, longitude: 127.45 },
  { name: 'andong', latitude: 36.55, longitude: 128.75 },
  { name: 'daegu', latitude: 35.85, longitude: 128.55 },
  { name: 'gyeongju', latitude: 35.85, longitude: 129.25 },
  { name: 'ulsan', latitude: 35.55, longitude: 129.35 },
  { name: 'busan_central', latitude: 35.15, longitude: 129.05 },
  { name: 'busan_east', latitude: 35.15, longitude: 129.15 },
  { name: 'changwon', latitude: 35.25, longitude: 128.65 },
  { name: 'tongyeong', latitude: 34.85, longitude: 128.45 },
  { name: 'jeonju', latitude: 35.85, longitude: 127.15 },
  { name: 'gunsan', latitude: 35.95, longitude: 126.75 },
  { name: 'gwangju', latitude: 35.15, longitude: 126.85 },
  { name: 'suncheon', latitude: 34.95, longitude: 127.55 },
  { name: 'yeosu', latitude: 34.75, longitude: 127.75 },
  { name: 'mokpo', latitude: 34.75, longitude: 126.35 },
  { name: 'jeju_central', latitude: 33.45, longitude: 126.45 },
  { name: 'jeju_east', latitude: 33.45, longitude: 126.95 },
  { name: 'seogwipo', latitude: 33.25, longitude: 126.55 },
];

const NAVIGATION_STEPS = [
  { name: 'initial_entry', latitudeOffset: 0.0000, longitudeOffset: 0.0000 },
  { name: 'pan_east', latitudeOffset: 0.0000, longitudeOffset: 0.0060 },
  { name: 'pan_north_east', latitudeOffset: 0.0040, longitudeOffset: 0.0060 },
  { name: 'pan_west', latitudeOffset: 0.0040, longitudeOffset: -0.0050 },
  { name: 'pan_south', latitudeOffset: -0.0040, longitudeOffset: -0.0050 },
  { name: 'return_to_initial', latitudeOffset: 0.0000, longitudeOffset: 0.0000 },
];

function hubForRequest(vuId) {
  const homeIndex = (vuId - 1) % TOURISM_HUBS.length;
  if (Math.random() >= REGION_CHANGE_RATE) {
    return { hub: TOURISM_HUBS[homeIndex], movement: 'local_navigation' };
  }

  const offset = 1 + Math.floor(Math.random() * (TOURISM_HUBS.length - 1));
  return {
    hub: TOURISM_HUBS[(homeIndex + offset) % TOURISM_HUBS.length],
    movement: 'region_change',
  };
}

function viewportForVu(vuId, hub, step) {
  const position = Math.floor((vuId - 1) / TOURISM_HUBS.length) % 25;
  const row = Math.floor(position / 5) - 2;
  const column = (position % 5) - 2;
  const latitudeOffset = (Math.random() * 2 - 1) * MAX_RANDOM_CENTER_OFFSET;
  const longitudeOffset = (Math.random() * 2 - 1) * MAX_RANDOM_CENTER_OFFSET;
  const centerLatitude = hub.latitude + row * 0.0012 + step.latitudeOffset + latitudeOffset;
  const centerLongitude = hub.longitude + column * 0.0018 + step.longitudeOffset + longitudeOffset;

  return {
    south: (centerLatitude - VIEWPORT_LATITUDE_SPAN / 2).toFixed(5),
    west: (centerLongitude - VIEWPORT_LONGITUDE_SPAN / 2).toFixed(5),
    north: (centerLatitude + VIEWPORT_LATITUDE_SPAN / 2).toFixed(5),
    east: (centerLongitude + VIEWPORT_LONGITUDE_SPAN / 2).toFixed(5),
  };
}

export const options = {
  scenarios: {
    map_zoom_15_nationwide_tourism: {
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
  const vuId = exec.vu.idInTest;
  const session = `${SESSION_PREFIX}${String(vuId).padStart(3, '0')}`;
  const selected = hubForRequest(vuId);
  const step = NAVIGATION_STEPS[exec.vu.iterationInScenario % NAVIGATION_STEPS.length];
  const viewport = viewportForVu(vuId, selected.hub, step);
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
      request: 'map_zoom_15_nationwide_tourism',
      region: selected.hub.name,
      movement: selected.movement,
      flow: step.name,
      map_mode: 'detail',
      zoom: '15',
      query_version: `zoom_15_nationwide_tourism_${CACHE_MODE}`,
    },
  });

  check(response, {
    'map zoom 15 nationwide tourism returns 200': (result) => result.status === 200,
  });

  if (response.status !== 200) {
    console.error(`MAP NATIONWIDE TOURISM FAILED | status=${response.status}`);
  }

  sleep(1);
}
