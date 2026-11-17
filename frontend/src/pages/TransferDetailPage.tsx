import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getTransferById } from '../api/transfers';
import type { TransferResponse } from '../types/api';
import { StatusBadge } from '../components/StatusBadge';
import { ApiError } from '../api/client';
import { friendlyMessage } from '../api/errorMessages';

export function TransferDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [transfer, setTransfer] = useState<TransferResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    getTransferById(id)
      .then(setTransfer)
      .catch((err) => setError(err instanceof ApiError ? friendlyMessage(err.errorCode, err.message) : 'Transfer bulunamadı.'));
  }, [id]);

  if (error) return <div className="result-banner error">{error}</div>;
  if (!transfer) return <div className="loading">Yükleniyor…</div>;

  return (
    <div className="card detail-card">
      <Link to="/transfers" className="back-link">&larr; Transferlerime dön</Link>
      <h2>Transfer Detayı</h2>
      <dl>
        <dt>Durum</dt><dd><StatusBadge status={transfer.status} /></dd>
        <dt>Tutar</dt><dd>{transfer.amount} {transfer.currency}</dd>
        <dt>Alıcı IBAN</dt><dd>{transfer.destinationIban}</dd>
        <dt>Açıklama</dt><dd>{transfer.description || '—'}</dd>
        <dt>Oluşturulma</dt><dd>{new Date(transfer.createdAt).toLocaleString('tr-TR')}</dd>
        {transfer.completedAt && (<><dt>Tamamlanma</dt><dd>{new Date(transfer.completedAt).toLocaleString('tr-TR')}</dd></>)}
        {transfer.failureReason && (<><dt>Hata Nedeni</dt><dd>{transfer.failureReason}</dd></>)}
        <dt>Transfer ID</dt><dd className="mono">{transfer.id}</dd>
      </dl>
    </div>
  );
}
