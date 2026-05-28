import type { StatsOverview } from "@/shared/lib/api-client";

export const mockStatsOverview: StatsOverview = {
  total_logs: 1234567,
  total_errors: 12345,
  error_rate: 0.01,
  service_count: 5,
  active_services: 4,
  silent_services: 1,
  log_volume_trend: Array.from({ length: 24 }, (_, i) => ({
    timestamp: `2026-05-24T${String(i).padStart(2, "0")}:00:00Z`,
    total: Math.floor(40000 + Math.random() * 20000),
    errors: Math.floor(300 + Math.random() * 400),
  })),
  level_distribution: {
    DEBUG: 800000,
    INFO: 350000,
    WARN: 50000,
    ERROR: 12345,
    FATAL: 2,
  },
  top_services_by_volume: [
    { service_name: "gateway", total_logs: 450000 },
    { service_name: "order-service", total_logs: 380000 },
    { service_name: "user-service", total_logs: 200000 },
    { service_name: "payment-service", total_logs: 150000 },
    { service_name: "notification-service", total_logs: 54567 },
  ],
  top_errors: [
    {
      signature: "a1b2c3d4e5f6789012345678abcdef01",
      exception_type: "IllegalStateException",
      normalized_msg: "insufficient stock for item <N>",
      count: 567,
      service_name: "order-service",
    },
    {
      signature: "b2c3d4e5f6789012345678abcdef0123",
      exception_type: "SocketTimeoutException",
      normalized_msg: "Read timed out after <N>ms",
      count: 234,
      service_name: "payment-service",
    },
  ],
};
