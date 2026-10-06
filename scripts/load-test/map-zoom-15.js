import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SESSION_PREFIX = __ENV.SESSION_PREFIX || 'k6-local-session-';
const MAX_VUS = Number(__ENV.VUS || 500);
const REGION_COUNT = Number(__ENV.REGION_COUNT || 10);

if (!Number.isInteger(MAX_VUS) || MAX_VUS < 1 || MAX_VUS > 999) {
  throw new Error('VUS는 1 이상 999 이하의 정수여야 합니다.');
}

const INITIAL_VUS = Math.max(1, Math.ceil(MAX_VUS / 10));
const QUARTER_VUS = Math.max(1, Math.ceil(MAX_VUS / 4));
const HALF_VUS = Math.max(1, Math.ceil(MAX_VUS / 2));

// 실제 사용자들이 주로 조회할 법한 전국 관광 밀집 지역을 시작점으로 사용한다.
const HOTSPOTS = [
  { name: 'city_hall', latitude: 37.5665, longitude: 126.9780 },
  { name: 'hongdae', latitude: 37.5563, longitude: 126.9236 },
  { name: 'seongsu', latitude: 37.5446, longitude: 127.0558 },
  { name: 'gangnam', latitude: 37.4979, longitude: 127.0276 },
  { name: 'busan_haeundae', latitude: 35.1587, longitude: 129.1604 },
  { name: 'daegu', latitude: 35.8690, longitude: 128.5940 },
  { name: 'daejeon', latitude: 36.3504, longitude: 127.3845 },
  { name: 'gwangju', latitude: 35.1595, longitude: 126.8526 },
  { name: 'jeonju', latitude: 35.8242, longitude: 127.1480 },
  { name: 'jeju', latitude: 33.4996, longitude: 126.5312 },
];

if (!Number.isInteger(REGION_COUNT) || REGION_COUNT < 1 || REGION_COUNT > HOTSPOTS.length) {
  throw new Error(`REGION_COUNT는 1 이상 ${HOTSPOTS.length} 이하의 정수여야 합니다.`);
}

const ACTIVE_HOTSPOTS = HOTSPOTS.slice(0, REGION_COUNT);

// zoom 15 상세 조회에서 화면을 조금씩 이동하는 흐름을 표현한다.
// 마지막 단계는 처음 bounds로 돌아가 반복 조회까지 같은 흐름에서 측정한다.
const NAVIGATION_STEPS = [
  { name: 'initial_entry', latitudeOffset: 0.0000, longitudeOffset: 0.0000 },
  { name: 'pan_east', latitudeOffset: 0.0000, longitudeOffset: 0.0060 },
  { name: 'pan_north_east', latitudeOffset: 0.0040, longitudeOffset: 0.0060 },
  { name: 'pan_west', latitudeOffset: 0.0040, longitudeOffset: -0.0050 },
  { name: 'pan_south', latitudeOffset: -0.0040, longitudeOffset: -0.0050 },
  { name: 'return_to_initial', latitudeOffset: 0.0000, longitudeOffset: 0.0000 },
];

const VIEWPORT_LATITUDE_SPAN = 0.0300;
const VIEWPORT_LONGITUDE_SPAN = 0.0600;
const MAX_RANDOM_CENTER_OFFSET = 0.0002;

function viewportForVu(vuId, step) {
  const hotspot = ACTIVE_HOTSPOTS[(vuId - 1) % ACTIVE_HOTSPOTS.length];

  // 사용자마다 지도 중심이 조금씩 다른 상황을 만든다.
  // 핫스팟별 5 x 5 위치를 만들어 전체 250개의 시작 bounds를 재사용한다.
  const position = Math.floor((vuId - 1) / ACTIVE_HOTSPOTS.length) % 25;
  const row = Math.floor(position / 5) - 2;
  const column = (position % 5) - 2;
  const latitudeJitter = row * 0.0012;
  const longitudeJitter = column * 0.0018;

  const centerLatitude = hotspot.latitude + latitudeJitter + step.latitudeOffset;
  const centerLongitude = hotspot.longitude + longitudeJitter + step.longitudeOffset;

  return {
    hotspot: hotspot.name,
    south: centerLatitude - VIEWPORT_LATITUDE_SPAN / 2,
    west: centerLongitude - VIEWPORT_LONGITUDE_SPAN / 2,
    north: centerLatitude + VIEWPORT_LATITUDE_SPAN / 2,
    east: centerLongitude + VIEWPORT_LONGITUDE_SPAN / 2,
  };
}

function mapQuery(viewport) {
  // 실제 드래그·화면 크기 차이로 생기는 미세한 bounds 변화를 표현한다.
  // 대략 수십 m 내의 이동이라 화면은 거의 같지만 exact-bounds 캐시 키는 달라진다.
  const latitudeOffset = (Math.random() * 2 - 1) * MAX_RANDOM_CENTER_OFFSET;
  const longitudeOffset = (Math.random() * 2 - 1) * MAX_RANDOM_CENTER_OFFSET;
  const bounds = {
    south: (viewport.south + latitudeOffset).toFixed(5),
    west: (viewport.west + longitudeOffset).toFixed(5),
    north: (viewport.north + latitudeOffset).toFixed(5),
    east: (viewport.east + longitudeOffset).toFixed(5),
  };
  const query = [
    `south=${bounds.south}`,
    `west=${bounds.west}`,
    `north=${bounds.north}`,
    `east=${bounds.east}`,
    'zoom=15',
  ].join('&');
  return { bounds, query };
}

export const options = {
  scenarios: {
    map_zoom_15_navigation: {
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
  const navigationStep = NAVIGATION_STEPS[exec.vu.iterationInScenario % NAVIGATION_STEPS.length];
  const viewport = viewportForVu(vuId, navigationStep);
  const mapRequest = mapQuery(viewport);
  const response = http.get(
    `${BASE_URL}/api/v1/map/contents?${mapRequest.query}`,
    {
      headers: {
        Cookie: `KGB_SESSION=${session}`,
      },
      tags: {
        name: '/api/v1/map/contents',
        request: 'map_zoom_15_navigation',
        region: viewport.hotspot,
        flow: navigationStep.name,
        map_mode: 'detail',
        zoom: '15',
        query_version: 'zoom_15_detail',
      },
    },
  );

  check(response, {
    'map_zoom_15 returns 200': (result) => result.status === 200,
  });

  if (response.status !== 200) {
    console.error(`MAP ZOOM 15 FAILED | status=${response.status}`);
  }
  if (response.timings.duration >= 1000) {
    console.warn(
      `MAP ZOOM 15 SLOW | duration=${response.timings.duration.toFixed(2)}ms`
      + ` | region=${viewport.hotspot}`
      + ` | flow=${navigationStep.name}`
      + ` | bounds=${JSON.stringify(mapRequest.bounds)}`,
    );
  }

  sleep(1);
}
