import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 1);
const SESSION_PREFIX = __ENV.SESSION_PREFIX || 'k6-local-session-';

if (![1, 2, 5, 10, 20].includes(VUS)) {
  throw new Error('VUS는 1, 2, 5, 10, 20 중 하나여야 합니다.');
}

const generationDuration = new Trend('generation_e2e_duration', true);
const generationCompleted = new Counter('generation_completed');
const generationFailed = new Counter('generation_failed');

export const options = {
  scenarios: {
    guidebook_generation: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: '2m',
      exec: 'generate',
    },
  },
  thresholds: {
    generation_failed: ['count==0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

function sessionForVu() {
  return `${SESSION_PREFIX}${String(exec.vu.idInTest).padStart(3, '0')}`;
}

function dateAfterToday(days) {
  const date = new Date(Date.now() + days * 24 * 60 * 60 * 1000);
  return date.toISOString().slice(0, 10);
}

function getCsrf(session) {
  const response = http.get(`${BASE_URL}/api/v1/auth/csrf`, {
    headers: {
      Cookie: `KGB_SESSION=${session}`,
    },
    tags: {
      request: 'csrf',
    },
  });

  const issued = check(response, {
    'csrf issued': (result) => result.status === 200,
  });

  if (!issued) {
    throw new Error(`CSRF FAILED | status=${response.status}`);
  }

  const token = response.cookies['XSRF-TOKEN']?.[0]?.value;
  if (!token) {
    throw new Error('XSRF-TOKEN 쿠키를 받지 못했습니다.');
  }

  return token;
}

export function generate() {
  const session = sessionForVu();
  const csrf = getCsrf(session);
  const idempotencyKey = `local-k6-${VUS}vu-${exec.vu.idInTest}-${Date.now()}`;
  const payload = JSON.stringify({
    province: '서울특별시',
    city: '종로구',
    start_date: __ENV.TRIP_START_DATE || dateAfterToday(2),
    end_date: __ENV.TRIP_END_DATE || dateAfterToday(3),
    companion: 'FRIEND',
    people_count: 2,
  });
  const startedAt = Date.now();

  const createResponse = http.post(
    `${BASE_URL}/api/v1/guidebook-generations`,
    payload,
    {
      headers: {
        Cookie: `KGB_SESSION=${session}; XSRF-TOKEN=${csrf}`,
        'X-XSRF-TOKEN': csrf,
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      tags: {
        request: 'generation_create',
      },
    },
  );

  const accepted = check(createResponse, {
    'generation accepted': (response) => response.status === 202,
  });

  if (!accepted) {
    generationFailed.add(1);
    console.error(`CREATE FAILED | VUs=${VUS} | status=${createResponse.status}`);
    return;
  }

  const jobId = createResponse.json()?.data?.job_id;
  if (!jobId) {
    generationFailed.add(1);
    console.error('NO JOB ID');
    return;
  }

  while (Date.now() - startedAt < 110000) {
    sleep(2);

    const statusResponse = http.get(
      `${BASE_URL}/api/v1/guidebook-generations/${jobId}`,
      {
        headers: {
          Cookie: `KGB_SESSION=${session}`,
        },
        tags: {
          name: '/api/v1/guidebook-generations/{jobId}',
          request: 'generation_status',
        },
      },
    );

    if (statusResponse.status !== 200) {
      console.error(`STATUS FAILED | job=${jobId} | status=${statusResponse.status}`);
      continue;
    }

    const status = statusResponse.json()?.data?.status;
    if (status === 'COMPLETED') {
      const duration = Date.now() - startedAt;
      generationDuration.add(duration);
      generationCompleted.add(1);
      console.log(`COMPLETED | job=${jobId} | duration=${duration}ms`);
      return;
    }

    if (status === 'FAILED') {
      generationFailed.add(1);
      console.error(`GENERATION FAILED | job=${jobId}`);
      return;
    }
  }

  generationFailed.add(1);
  console.error(`TIMEOUT | job=${jobId}`);
}
