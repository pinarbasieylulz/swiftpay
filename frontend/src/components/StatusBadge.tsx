const labels: Record<string, string> = {
  COMPLETED: 'Tamamlandı',
  PENDING: 'Beklemede',
  FAILED: 'Başarısız',
};

export function StatusBadge({ status }: { status: string }) {
  return <span className={`status-badge status-${status.toLowerCase()}`}>{labels[status] ?? status}</span>;
}
