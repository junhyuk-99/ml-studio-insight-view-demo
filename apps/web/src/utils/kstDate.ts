const KST_TIME_ZONE = 'Asia/Seoul';

function getKstDateParts(now: Date): { year: string; month: string; day: string } {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: KST_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now);

  const year = parts.find((part) => part.type === 'year')?.value;
  const month = parts.find((part) => part.type === 'month')?.value;
  const day = parts.find((part) => part.type === 'day')?.value;

  if (!year || !month || !day) {
    throw new Error('KST 날짜 파싱에 실패했습니다.');
  }

  return { year, month, day };
}

function pad2(value: number): string {
  return String(value).padStart(2, '0');
}

function lastDayOfMonth(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

export function getTodayKstDateText(now: Date = new Date()): string {
  const { year, month, day } = getKstDateParts(now);
  return `${year}-${month}-${day}`;
}

export function getCurrentKstMonthKey(now: Date = new Date()): string {
  const { year, month } = getKstDateParts(now);
  return `${year}-${month}`;
}

export function getCurrentKstMonthRange(now: Date = new Date()): {
  startDate: string;
  endDate: string;
  monthKey: string;
} {
  const monthKey = getCurrentKstMonthKey(now);
  return {
    monthKey,
    startDate: `${monthKey}-01`,
    endDate: getTodayKstDateText(now),
  };
}

export function shiftMonth(monthKey: string, amount: number): string {
  const [yearText, monthText] = monthKey.split('-');
  const year = Number(yearText);
  const month = Number(monthText);

  if (!Number.isInteger(year) || !Number.isInteger(month) || month < 1 || month > 12) {
    return monthKey;
  }

  const base = new Date(Date.UTC(year, month - 1 + amount, 1));
  return `${base.getUTCFullYear()}-${pad2(base.getUTCMonth() + 1)}`;
}

export function resolveMonthRangeWithKstToday(
  monthKey: string,
  now: Date = new Date(),
): { startDate: string; endDate: string } {
  const normalized = monthKey.trim();
  const match = /^(\d{4})-(\d{2})$/.exec(normalized);
  if (!match) {
    const currentRange = getCurrentKstMonthRange(now);
    return {
      startDate: currentRange.startDate,
      endDate: currentRange.endDate,
    };
  }

  const year = Number(match[1]);
  const month = Number(match[2]);
  const firstDate = `${match[1]}-${match[2]}-01`;

  if (!Number.isInteger(year) || !Number.isInteger(month) || month < 1 || month > 12) {
    const currentRange = getCurrentKstMonthRange(now);
    return {
      startDate: currentRange.startDate,
      endDate: currentRange.endDate,
    };
  }

  const currentMonthKey = getCurrentKstMonthKey(now);
  if (normalized === currentMonthKey) {
    return {
      startDate: firstDate,
      endDate: getTodayKstDateText(now),
    };
  }

  return {
    startDate: firstDate,
    endDate: `${match[1]}-${match[2]}-${pad2(lastDayOfMonth(year, month))}`,
  };
}
