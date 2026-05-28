import type { TopError, ErrorClusterSummary } from "@/shared/lib/api-client";

export const mockTopErrors: TopError[] = [
  {
    rank: 1,
    signature: "a1b2c3d4e5f6789012345678abcdef01",
    exception_type: "java.lang.IllegalStateException",
    normalized_msg: "insufficient stock for item <N>",
    sample_message: "insufficient stock for item 42",
    service_name: "order-service",
    total_count: 567,
    first_seen: "2026-05-24T08:00:00.123Z",
    last_seen: "2026-05-24T19:59:00.456Z",
    trend: "rising",
    affected_services: ["order-service", "gateway"],
  },
  {
    rank: 2,
    signature: "b2c3d4e5f6789012345678abcdef0123",
    exception_type: "java.net.SocketTimeoutException",
    normalized_msg: "Read timed out after <N>ms",
    sample_message: "Read timed out after 30000ms",
    service_name: "payment-service",
    total_count: 234,
    first_seen: "2026-05-24T06:00:00.000Z",
    last_seen: "2026-05-24T19:45:00.000Z",
    trend: "stable",
    affected_services: ["payment-service"],
  },
  {
    rank: 3,
    signature: "c3d4e5f6789012345678abcdef012345",
    exception_type: "NullPointerException",
    normalized_msg: "Cannot invoke method on null reference at <PATH>",
    sample_message:
      "Cannot invoke String.length() on null reference at UserService.java:142",
    service_name: "user-service",
    total_count: 89,
    first_seen: "2026-05-24T10:00:00.000Z",
    last_seen: "2026-05-24T18:30:00.000Z",
    trend: "falling",
    affected_services: ["user-service"],
  },
];

export const mockErrorClusters: ErrorClusterSummary = {
  summary: {
    total_clusters: 42,
    total_errors: 12345,
    new_clusters_24h: 3,
    resolved_clusters_24h: 5,
  },
  daily_trend: Array.from({ length: 7 }, (_, i) => ({
    date: `2026-05-${18 + i}`,
    total_errors: Math.floor(1000 + Math.random() * 500),
    unique_clusters: Math.floor(30 + Math.random() * 15),
  })),
  top_clusters: [
    {
      rank: 1,
      signature: "a1b2c3d4e5f6789012345678abcdef01",
      exception_type: "IllegalStateException",
      normalized_msg: "insufficient stock for item <N>",
      total_count: 1234,
      occurrence_days: 5,
      avg_per_day: 246.8,
      first_seen: "2026-05-20T08:00:00Z",
      last_seen: "2026-05-24T19:59:00Z",
    },
    {
      rank: 2,
      signature: "b2c3d4e5f6789012345678abcdef0123",
      exception_type: "SocketTimeoutException",
      normalized_msg: "Read timed out after <N>ms",
      total_count: 890,
      occurrence_days: 4,
      avg_per_day: 222.5,
      first_seen: "2026-05-21T06:00:00Z",
      last_seen: "2026-05-24T19:45:00Z",
    },
  ],
};
