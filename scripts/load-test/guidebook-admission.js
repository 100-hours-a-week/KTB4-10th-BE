import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// 사용자별 진행 작업 제한을 피하도록 요청마다 서로 다른 fixture 회원을 사용한다.
const accepted = new Counter('be_accepted');
const rejected = new Counter('be_rejected');
const admissionDuration = new Trend('admission_duration', true);
export const options = {
  scenarios: {
    admission: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 10),
      timeUnit: '1s',
      duration: __ENV.DURATION || '10m',
      preAllocatedVUs: 30,
      maxVUs: 200,
      gracefulStop: '45s',
    },
  },
  thresholds: {
    dropped_iterations: ['count==0'],
    be_rejected: ['count==0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
};

export default function () {
  const sequence = exec.scenario.iterationInTest + 1;
  const session = `${__ENV.SESSION_PREFIX}${String(sequence).padStart(3, '0')}`;
  const csrfResponse = http.get(`${__ENV.BASE_URL}/api/v1/auth/csrf`, {
    headers: { Cookie: `KGB_SESSION=${session}` },
    tags: { request: 'csrf' },
  });
  const csrf = csrfResponse.cookies['XSRF-TOKEN']?.[0]?.value;
  if (csrfResponse.status !== 200 || !csrf) {
    rejected.add(1);
    console.error(`csrf_failed sequence=${sequence} status=${csrfResponse.status}`);
    return;
  }
  const date = days => new Date(Date.now() + days * 86400000).toISOString().slice(0, 10);
  const response = http.post(`${__ENV.BASE_URL}/api/v1/guidebook-generations`, JSON.stringify({
    province: '서울특별시', city: '종로구',
    start_date: date(2), end_date: date(3), companion: 'FRIEND', people_count: 2,
  }), {
    headers: {
      Cookie: `KGB_SESSION=${session}; XSRF-TOKEN=${csrf}`,
      'X-XSRF-TOKEN': csrf,
      'Content-Type': 'application/json',
      'Idempotency-Key': `${__ENV.RUN_ID}-${sequence}`,
    },
    tags: { request: 'generation_create' },
    timeout: '40s',
  });
  admissionDuration.add(response.timings.duration);
  const ok = check(response, { 'BE accepted 202': result => result.status === 202 });
  accepted.add(ok ? 1 : 0);
  rejected.add(ok ? 0 : 1);
  if (!ok) console.error(`create_failed sequence=${sequence} status=${response.status}`);
}
