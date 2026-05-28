import { format, formatDistanceToNow, parseISO } from "date-fns";
import { zhCN } from "date-fns/locale";

export function formatTimestamp(iso: string): string {
  return format(parseISO(iso), "yyyy-MM-dd HH:mm:ss.SSS");
}

export function formatRelativeTime(iso: string): string {
  return formatDistanceToNow(parseISO(iso), { addSuffix: true, locale: zhCN });
}

export function formatNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return n.toString();
}

export function formatRate(rate: number): string {
  return `${(rate * 100).toFixed(1)}%`;
}
