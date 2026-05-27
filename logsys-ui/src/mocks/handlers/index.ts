import { http, HttpResponse, delay } from "msw";
import { mockLogPage } from "../fixtures/logs.fixture";
import { mockServices, mockServiceDetail } from "../fixtures/services.fixture";
import { mockTopErrors, mockErrorClusters } from "../fixtures/errors.fixture";
import { mockStatsOverview } from "../fixtures/stats.fixture";

export const handlers = [
  http.post("/api/v1/logs/query", async () => {
    await delay(150);
    return HttpResponse.json(mockLogPage);
  }),

  http.get("/api/v1/services", async ({ request }) => {
    await delay(100);
    const url = new URL(request.url);
    const status = url.searchParams.get("status");
    const items = status
      ? mockServices.filter((s) => s.status === status)
      : mockServices;
    return HttpResponse.json({ items });
  }),

  http.get("/api/v1/services/:name", async ({ params }) => {
    await delay(120);
    const { name } = params;
    if (name === "unknown-service") {
      return HttpResponse.json(
        { code: "NOT_FOUND", message: `Service '${name}' not found` },
        { status: 404 }
      );
    }
    return HttpResponse.json(mockServiceDetail);
  }),

  http.get("/api/v1/errors/top", async () => {
    await delay(100);
    return HttpResponse.json({ items: mockTopErrors });
  }),

  http.get("/api/v1/errors/clusters", async () => {
    await delay(130);
    return HttpResponse.json(mockErrorClusters);
  }),

  http.get("/api/v1/stats/overview", async () => {
    await delay(100);
    return HttpResponse.json(mockStatsOverview);
  }),
];
