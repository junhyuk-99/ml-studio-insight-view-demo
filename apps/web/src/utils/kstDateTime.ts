const KST_TIME_ZONE = 'Asia/Seoul';
const KST_OFFSET_HOURS = 9;

function pad2(value: number): string {
  return String(value).padStart(2, '0');
}

export function formatKstDateTime(utcIsoText: string | null | undefined): string {
  if (!utcIsoText) {
    return '-';
  }
  const date = new Date(utcIsoText);
  if (Number.isNaN(date.getTime())) {
    return '-';
  }
  return date.toLocaleString('ko-KR', {
    timeZone: KST_TIME_ZONE,
    hour12: false,
  });
}

export function formatKstAxisLabel(utcIsoText: string | null | undefined): string {
  if (!utcIsoText) {
    return '-';
  }
  const date = new Date(utcIsoText);
  if (Number.isNaN(date.getTime())) {
    return '-';
  }
  return date.toLocaleString('ko-KR', {
    timeZone: KST_TIME_ZONE,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

export function toKstDateTimeInputValue(utcIsoText: string | null | undefined): string {
  if (!utcIsoText) {
    return '';
  }
  const utcDate = new Date(utcIsoText);
  if (Number.isNaN(utcDate.getTime())) {
    return '';
  }

  const kstDate = new Date(utcDate.getTime() + KST_OFFSET_HOURS * 60 * 60 * 1000);
  const datePart = [
    kstDate.getUTCFullYear(),
    pad2(kstDate.getUTCMonth() + 1),
    pad2(kstDate.getUTCDate()),
  ].join('-');
  return `${datePart}T${pad2(kstDate.getUTCHours())}:${pad2(kstDate.getUTCMinutes())}`;
}

export function toUtcIsoFromKstDateTimeInput(kstDateTimeText: string): string | null {
  const normalized = kstDateTimeText.trim();
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(normalized);
  if (!match) {
    return null;
  }

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const hour = Number(match[4]);
  const minute = Number(match[5]);

  if (
    !Number.isInteger(year) ||
    !Number.isInteger(month) ||
    !Number.isInteger(day) ||
    !Number.isInteger(hour) ||
    !Number.isInteger(minute) ||
    month < 1 ||
    month > 12 ||
    day < 1 ||
    day > 31 ||
    hour < 0 ||
    hour > 23 ||
    minute < 0 ||
    minute > 59
  ) {
    return null;
  }

  return new Date(Date.UTC(year, month - 1, day, hour - KST_OFFSET_HOURS, minute, 0, 0)).toISOString();
}

export function getCurrentKstDateTimeInput(now: Date = new Date()): string {
  return toKstDateTimeInputValue(now.toISOString());
}

export function shiftKstDateTimeInputHours(kstDateTimeText: string, hours: number): string {
  const utcIso = toUtcIsoFromKstDateTimeInput(kstDateTimeText);
  if (!utcIso) {
    return kstDateTimeText;
  }
  const date = new Date(utcIso);
  date.setUTCHours(date.getUTCHours() + hours);
  return toKstDateTimeInputValue(date.toISOString());
}
