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

const regions = [
  { limit: 0.35, name: 'seoul', latitude: 37.5665, longitude: 126.9780 },
  { limit: 0.50, name: 'incheon', latitude: 37.4563, longitude: 126.7052 },
  { limit: 0.65, name: 'busan', latitude: 35.1796, longitude: 129.0756 },
  { limit: 0.80, name: 'jeju', latitude: 33.4996, longitude: 126.5312 },
  { limit: 0.90, name: 'daegu', latitude: 35.8714, longitude: 128.6014 },
  { limit: 1.00, name: 'daejeon', latitude: 36.3504, longitude: 127.3845 },
];

export const options = {
  scenarios: {
    map_contents: {
      executor: 'ramping-vus',
      startVUs: SMOKE ? 1 : INITIAL_VUS,
      stages: SMOKE ? [{ target: 1, duration: '5s' }] : [
        { target: INITIAL_VUS, duration: '5s' },
        { target: QUARTER_VUS, duration: '5s' },
        { target: HALF_VUS, duration: '5s' },
        { target: MAX_VUS, duration: '10s' },
        { target: 0, duration: '5s' },
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

function selectRegion() {
  const random = Math.random();
  return regions.find((region) => random < region.limit);
}

function bounds(region, latitudeHalf, longitudeHalf, longitudeOffset = 0) {
  const longitude = region.longitude + longitudeOffset;
  return {
    south: (region.latitude - latitudeHalf).toFixed(5),
    west: (longitude - longitudeHalf).toFixed(5),
    north: (region.latitude + latitudeHalf).toFixed(5),
    east: (longitude + longitudeHalf).toFixed(5),
  };
}

function requestMap(session, region, flow, step, zoom, viewport) {
  const query = [
    `south=${viewport.south}`,
    `west=${viewport.west}`,
    `north=${viewport.north}`,
    `east=${viewport.east}`,
    `zoom=${zoom}`,
  ].join('&');

  const response = http.get(`${BASE_URL}/api/v1/map/contents?${query}`, {
    headers: {
      Cookie: `KGB_SESSION=${session}`,
    },
    tags: {
      name: '/api/v1/map/contents',
      request: 'map_contents',
      region: region.name,
      flow,
      step,
      map_mode: zoom <= 14 ? 'cluster' : 'detail',
      zoom: String(zoom),
    },
  });

  check(response, {
    'map_contents returns 200': (result) => result.status === 200,
  });

  if (response.status !== 200) {
    console.error(
      `MAP FAILED | flow=${flow} | region=${region.name} | zoom=${zoom}`
      + ` | status=${response.status}`,
    );
  }
}

export default function () {
  const session = `${SESSION_PREFIX}${String(exec.vu.idInTest).padStart(3, '0')}`;
  const region = selectRegion();
  const flowRandom = Math.random();

  if (flowRandom < 0.40) {
    requestMap(session, region, 'initial_entry', 'zoom_12', 12,
      bounds(region, 0.15, 0.20));
    sleep(3 + Math.random() * 2);
    return;
  }

  if (flowRandom < 0.80) {
    [-0.06, 0, 0.06].forEach((longitudeOffset, index) => {
      requestMap(session, region, 'pan', `area_${index + 1}`, 16,
        bounds(region, 0.03, 0.05, longitudeOffset));
      if (index < 2) {
        sleep(0.5 + Math.random());
      }
    });
    sleep(2 + Math.random() * 2);
    return;
  }

  [
    { zoom: 12, latitudeHalf: 0.15, longitudeHalf: 0.20 },
    { zoom: 14, latitudeHalf: 0.08, longitudeHalf: 0.10 },
    { zoom: 16, latitudeHalf: 0.03, longitudeHalf: 0.05 },
  ].forEach((level, index) => {
    requestMap(session, region, 'zoom_in', `zoom_${level.zoom}`, level.zoom,
      bounds(region, level.latitudeHalf, level.longitudeHalf));
    if (index < 2) {
      sleep(0.75 + Math.random() * 0.75);
    }
  });

  sleep(2 + Math.random() * 2);
}
