export const errorMessages: Record<string, string> = {
  AUTH_ERROR: 'Kullanıcı adı veya şifre hatalı.',
  TOO_MANY_ATTEMPTS: 'Çok fazla başarısız deneme yapıldı. Lütfen biraz sonra tekrar deneyin.',
  VALIDATION_ERROR: 'Girdiğiniz bilgilerde hata var.',
  ACCESS_DENIED: 'Bu işlemi yapmaya yetkiniz yok.',
  RESOURCE_NOT_FOUND: 'Aradığınız kayıt bulunamadı.',
  INSUFFICIENT_BALANCE: 'Hesabınızda yeterli bakiye yok.',
  PER_TRANSFER_LIMIT_EXCEEDED: 'Bu tutar, tek seferlik transfer limitini aşıyor.',
  DAILY_LIMIT_EXCEEDED: 'Günlük transfer limitiniz aşıldı.',
  MONTHLY_LIMIT_EXCEEDED: 'Aylık transfer limitiniz aşıldı.',
  SELF_TRANSFER_NOT_ALLOWED: 'Aynı hesaba transfer yapamazsınız.',
  DESTINATION_NOT_FOUND: 'Belirtilen IBAN sistemde bulunamadı.',
  TRANSFER_CONFLICT: 'İşlem çakıştı, lütfen tekrar deneyin.',
  INTERNAL_ERROR: 'Beklenmeyen bir sunucu hatası oluştu.',
};

export function friendlyMessage(errorCode: string, fallback: string): string {
  return errorMessages[errorCode] ?? fallback;
}
