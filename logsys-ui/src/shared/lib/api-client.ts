const API_BASE = import.meta.env.VITE_API_BASE ?? "";

class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public details?: Record<string, string>[] | null
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
  });

  const body = await res.json();

  if (!res.ok) {
    throw new ApiError(
      res.status,
      body.code ?? "UNKNOWN_ERROR",
      body.message ?? res.statusText,
      body.details
    );
  }

  return body as T;
}

// ── Types matching api-spec.md ──

export interface PageResult<T> {
  total: number;
  page: number;
  page_size: number;
  items: T[];
}

export interface LogEntry {
  timestamp: string;
  service_name: string;
  service_instance: string;
  source_host: string;
  source_type: string;
  level: string;
  logger: string;
  message: string;
  trace_id: string;
  span_id: string;
  exception_type: string | null;
  exception_message: string | null;
  exception_stacktrace: string | null;
  tags: Record<string, string>;
  fields: Record<string, unknown>;
}

export interface LogQueryRequest {
  service_name?: string;
  level?: string;
  keyword?: string;
  trace_id?: string;
  start_time: string;
  end_time: string;
  page?: number;
  page_size?: number;
  sort?: "asc" | "desc";
}

export interface ServiceInfo {
  name: string;
  description: string;
  language: string;
  last_log_at: string;
  log_rate_per_min: number;
  error_rate: number;
  status: "healthy" | "warning" | "critical" | "silent";
  first_seen: string;
  instance_count: number;
}

export interface ServiceDetail {
  name: string;
  description: string;
  language: string;
  status: string;
  first_seen: string;
  last_log_at: string;
  metrics: {
    total_logs: number;
    total_errors: number;
    error_rate: number;
    log_rate_per_min: number;
  };
  log_volume_trend: { timestamp: string; total: number; errors: number }[];
  top_errors: {
    signature: string;
    exception_type: string;
    normalized_msg: string;
    count: number;
  }[];
  recent_logs: {
    timestamp: string;
    level: string;
    message: string;
    trace_id: string;
  }[];
}

export interface TopError {
  rank: number;
  signature: string;
  exception_type: string;
  normalized_msg: string;
  sample_message: string;
  service_name: string;
  total_count: number;
  first_seen: string;
  last_seen: string;
  trend: "rising" | "falling" | "stable";
  affected_services: string[];
}

export interface ErrorClusterSummary {
  summary: {
    total_clusters: number;
    total_errors: number;
    new_clusters_24h: number;
    resolved_clusters_24h: number;
  };
  daily_trend: {
    date: string;
    total_errors: number;
    unique_clusters: number;
  }[];
  top_clusters: {
    rank: number;
    signature: string;
    exception_type: string;
    normalized_msg: string;
    total_count: number;
    occurrence_days: number;
    avg_per_day: number;
    first_seen: string;
    last_seen: string;
  }[];
}

export interface StatsOverview {
  total_logs: number;
  total_errors: number;
  error_rate: number;
  service_count: number;
  active_services: number;
  silent_services: number;
  log_volume_trend: { timestamp: string; total: number; errors: number }[];
  level_distribution: Record<string, number>;
  top_services_by_volume: { service_name: string; total_logs: number }[];
  top_errors: {
    signature: string;
    exception_type: string;
    normalized_msg: string;
    count: number;
    service_name: string;
  }[];
}

// ── API methods ──

export const api = {
  logs: {
    query: (body: LogQueryRequest) =>
      request<PageResult<LogEntry>>("/api/v1/logs/query", {
        method: "POST",
        body: JSON.stringify(body),
      }),
  },

  services: {
    list: (status?: string) =>
      request<{ items: ServiceInfo[] }>(
        `/api/v1/services${status ? `?status=${status}` : ""}`
      ),
    detail: (name: string, range?: string) =>
      request<ServiceDetail>(
        `/api/v1/services/${name}${range ? `?range=${range}` : ""}`
      ),
  },

  errors: {
    top: (params?: { range?: string; limit?: number; service_name?: string }) =>
      request<{ items: TopError[] }>(
        `/api/v1/errors/top?${new URLSearchParams(
          Object.entries(params ?? {}).filter(
            ([, v]) => v !== undefined
          ) as string[][]
        ).toString()}`
      ),
    clusters: (params?: { range?: string; service_name?: string }) =>
      request<ErrorClusterSummary>(
        `/api/v1/errors/clusters?${new URLSearchParams(
          Object.entries(params ?? {}).filter(
            ([, v]) => v !== undefined
          ) as string[][]
        ).toString()}`
      ),
  },

  stats: {
    overview: (range?: string) =>
      request<StatsOverview>(
        `/api/v1/stats/overview${range ? `?range=${range}` : ""}`
      ),
  },
};
