import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { getMyAccounts } from '../api/accounts';
import { createTransfer } from '../api/transfers';
import type { AccountResponse, TransferResponse } from '../types/api';
import { ApiError, NetworkError } from '../api/client';
import { friendlyMessage } from '../api/errorMessages';

function isValidIban(iban: string): boolean {
  const normalized = iban.replace(/\s+/g, '').toUpperCase();
  if (!/^TR\d{24}$/.test(normalized)) return false;
  const rearranged = normalized.slice(4) + normalized.slice(0, 4);
  const numeric = rearranged.replace(/[A-Z]/g, (ch) => String(ch.charCodeAt(0) - 55));
  let remainder = 0;
  for (let i = 0; i < numeric.length; i += 7) {
    remainder = Number(String(remainder) + numeric.slice(i, i + 7)) % 97;
  }
  return remainder === 1;
}

export function NewTransferPage() {
  const navigate = useNavigate();
  const [accounts, setAccounts] = useState<AccountResponse[]>([]);
  const [sourceAccountId, setSourceAccountId] = useState('');
  const [destinationIban, setDestinationIban] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [result, setResult] = useState<{ type: 'success'; data: TransferResponse } | { type: 'error'; message: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    getMyAccounts().then((accs) => {
      setAccounts(accs);
      if (accs.length > 0) setSourceAccountId(accs[0].id);
    });
  }, []);

  function validate(): boolean {
    const errors: Record<string, string> = {};
    const amountNum = Number(amount);
    if (!amount || Number.isNaN(amountNum) || amountNum <= 0) {
      errors.amount = 'Geçerli bir tutar girin (0\'dan büyük olmalı).';
    }
    if (!isValidIban(destinationIban)) {
      errors.destinationIban = 'Geçerli bir TR IBAN girin (26 karakter).';
    }
    if (!sourceAccountId) {
      errors.sourceAccountId = 'Kaynak hesap seçin.';
    }
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setResult(null);
    if (!validate()) return;

    setSubmitting(true);
    try {
      const transfer = await createTransfer({
        sourceAccountId,
        destinationIban: destinationIban.replace(/\s+/g, '').toUpperCase(),
        amount,
        description: description || undefined,
        idempotencyKey,
      });
      setResult({ type: 'success', data: transfer });
      setIdempotencyKey(crypto.randomUUID());
      setAmount('');
      setDestinationIban('');
      setDescription('');
    } catch (err) {
      if (err instanceof ApiError) {
        setResult({ type: 'error', message: friendlyMessage(err.errorCode, err.message) });
      } else if (err instanceof NetworkError) {
        setResult({ type: 'error', message: err.message });
      } else {
        setResult({ type: 'error', message: 'Beklenmeyen bir hata oluştu.' });
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="stack">
      <form className="card" onSubmit={handleSubmit}>
        <h2>Yeni Transfer</h2>

        <label>
          Kaynak Hesap
          <select value={sourceAccountId} onChange={(e) => setSourceAccountId(e.target.value)}>
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>{a.iban} — {a.balance} {a.currency}</option>
            ))}
          </select>
          {fieldErrors.sourceAccountId && <span className="field-error">{fieldErrors.sourceAccountId}</span>}
        </label>

        <label>
          Alıcı IBAN
          <input value={destinationIban} onChange={(e) => setDestinationIban(e.target.value)} placeholder="TR..." required />
          {fieldErrors.destinationIban && <span className="field-error">{fieldErrors.destinationIban}</span>}
        </label>

        <label>
          Tutar (TRY)
          <input type="number" step="0.01" min="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required />
          {fieldErrors.amount && <span className="field-error">{fieldErrors.amount}</span>}
        </label>

        <label>
          Açıklama (opsiyonel)
          <input value={description} onChange={(e) => setDescription(e.target.value)} maxLength={255} />
        </label>

        {result?.type === 'error' && <div className="result-banner error">{result.message}</div>}
        {result?.type === 'success' && (
          <div className="result-banner success">
            Transfer başarılı! Tutar: {result.data.amount} {result.data.currency} — Durum: {result.data.status}
            {' '}
            <button type="button" className="link-button" onClick={() => navigate(`/transfers/${result.data.id}`)}>
              Detayı gör
            </button>
          </div>
        )}

        <button type="submit" disabled={submitting || accounts.length === 0}>
          {submitting ? 'Gönderiliyor…' : 'Transferi Gönder'}
        </button>
      </form>
    </div>
  );
}
