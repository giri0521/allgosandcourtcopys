import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

/**
 * The load profile from the plan: 150 concurrent users, p95 under 500ms on list endpoints.
 *
 * ## Running it
 *
 *   docker compose up -d
 *   cd backend && ./mvnw spring-boot:run
 *   k6 run -e TOKEN="$ACCESS_TOKEN" load/browse.js
 *
 * `TOKEN` is an access token for an approved account. It has to be supplied rather than obtained
 * here because signing in requires an OTP, and the code is only ever sent to a phone — in
 * development, printed to the backend log by the mock provider. Get one by signing in through the
 * web app and copying the token, or by running the OTP flow with curl. Access tokens live 15
 * minutes, so re-fetch before a long run.
 *
 * Use an **admin** token to include the reporting endpoints; a member token will make those 403 and
 * the run will report them as failures, which is the point of `ADMIN=false`.
 *
 * ## What this exercises, and what it does not
 *
 * Reads only. Uploads are deliberately excluded: a load test that writes needs a disposable
 * database and object store, and the interesting question here is whether browsing stays fast for
 * an office of 150 people, not whether MinIO can absorb bulk writes.
 *
 * Document *downloads* are also excluded, and that is not an oversight. The server only issues a
 * presigned URL; the bytes travel from object storage straight to the browser and never touch
 * Spring. Measuring the link-issuing call is measuring a signature computation, which tells you
 * nothing about download throughput — that is object storage's problem and belongs in its own test.
 */

const BASE = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const TOKEN = __ENV.TOKEN;
const IS_ADMIN = (__ENV.ADMIN || 'true') !== 'false';

if (!TOKEN) {
  throw new Error('Set TOKEN to an access token for an approved account — see the header comment.');
}

/** Separated from the overall trend so a slow report cannot hide a slow folder listing. */
const listDuration = new Trend('list_endpoint_duration', true);
const reportDuration = new Trend('report_endpoint_duration', true);
const failures = new Rate('failed_requests');

export const options = {
  stages: [
    { duration: '1m', target: 50 }, // warm the pool and the JIT
    { duration: '2m', target: 150 }, // the number in the plan
    { duration: '3m', target: 150 }, // hold — this is the measurement
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    // The plan's figure, asserted rather than eyeballed: k6 exits non-zero if it is missed, so
    // this can run in CI without anyone reading the output.
    'list_endpoint_duration': ['p(95)<500'],
    // Reports aggregate over every file and download, so they are allowed to be slower than a
    // list — but not slow enough that an admin thinks the screen has hung.
    'report_endpoint_duration': ['p(95)<2000'],
    'failed_requests': ['rate<0.01'],
    'http_req_failed': ['rate<0.01'],
  },
};

const params = {
  headers: { Authorization: `Bearer ${TOKEN}` },
  tags: { name: 'authenticated' },
};

/**
 * Runs once. Finds a real department and folder so the test exercises the same query plans a user
 * would, rather than a page of nothing.
 */
export function setup() {
  const departments = http.get(`${BASE}/departments`, params);
  check(departments, { 'setup: departments readable': (r) => r.status === 200 }) ||
    fail(`Could not read departments (${departments.status}) — is TOKEN valid and unexpired?`);

  const all = departments.json();
  const withFiles = all.find((d) => (d.fileCount || 0) > 0) || all[0];

  const folders = http.get(`${BASE}/departments/${withFiles.id}/folders`, params);
  const folder = folders.status === 200 ? (folders.json()[0] || null) : null;

  return { departmentId: withFiles.id, folderId: folder ? folder.id : null };
}

export default function (data) {
  // Roughly the shape of real use: people browse and search far more than they administer.
  group('browse', () => {
    measureList(http.get(`${BASE}/departments`, params), 'departments');
    measureList(
      http.get(`${BASE}/departments/${data.departmentId}/folders`, params),
      'department folders',
    );

    if (data.folderId) {
      measureList(http.get(`${BASE}/folders/${data.folderId}/files`, params), 'folder files');
    }
  });

  group('search', () => {
    // A two-character fragment is the worst case for the trigram index — the shortest query the
    // server accepts, and the one that matches the most rows.
    measureList(http.get(`${BASE}/files/search?q=or`, params), 'search');
    measureList(http.get(`${BASE}/files/recent?size=5`, params), 'recent');
  });

  group('personal', () => {
    measureList(http.get(`${BASE}/files/my-uploads`, params), 'my uploads');
    measureList(http.get(`${BASE}/favorites`, params), 'favorites');
    // The bell polls this on every screen, so under 150 users it is the most-called endpoint here.
    measureList(http.get(`${BASE}/notifications/unread-count`, params), 'unread count');
  });

  if (IS_ADMIN) {
    group('admin', () => {
      measureReport(http.get(`${BASE}/admin/stats`, params), 'stats');
      measureReport(http.get(`${BASE}/admin/reports`, params), 'reports');
      measureList(http.get(`${BASE}/admin/audit-logs?page=0&size=50`, params), 'audit log');
    });
  }

  // A user reads what they opened before clicking again; hammering with no pause measures the
  // server's behaviour under a load no office produces.
  sleep(Math.random() * 3 + 1);
}

function measureList(response, name) {
  record(response, name, listDuration);
}

function measureReport(response, name) {
  record(response, name, reportDuration);
}

function record(response, name, trend) {
  const ok = check(response, {
    [`${name}: 200`]: (r) => r.status === 200,
  });

  failures.add(!ok);
  if (ok) {
    trend.add(response.timings.duration);
  }
}

function fail(message) {
  throw new Error(message);
}
