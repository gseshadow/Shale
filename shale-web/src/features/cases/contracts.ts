export type CaseOverview = {
  caseId: number; caseNumber: string | null; caseName: string;
  status: { id: number; name: string; color: string | null } | null;
  practiceArea: { id: number; name: string } | null;
  responsibleAttorney: { userId: number; displayName: string } | null;
  primaryLegalAssistant: { userId: number; displayName: string } | null;
  updatedAt: string | null;
};
export type CasePage = { items: CaseOverview[]; page: number; size: number; hasMore: boolean };
export class InvalidCaseResponse extends Error {}
const fail = (): never => { throw new InvalidCaseResponse(); };
function object(value: unknown, keys: string[]): Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) return fail();
  const record = value as Record<string, unknown>;
  if (Object.keys(record).length !== keys.length || !keys.every(key => Object.hasOwn(record, key))) return fail();
  return record;
}
export function sqlId(value: unknown): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 1 || value > 2147483647) return fail();
  return value;
}
function text(value: unknown, max: number): string {
  if (typeof value !== 'string' || value.length > max) return fail();
  return value;
}
function nullable<T>(value: unknown, parse: (value: unknown) => T): T | null { return value === null ? null : parse(value); }
function localTimestamp(value: unknown): string {
  const result = text(value, 29);
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?$/.exec(result);
  if (!match) return fail();
  const [year, month, day, hour, minute, second] = match.slice(1, 7).map(value => Number(value ?? 0));
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (year < 1 || month < 1 || month > 12 || day < 1 || day > days[month - 1] || hour > 23 || minute > 59 || second > 59) return fail();
  return result;
}
function user(value: unknown) {
  const row = object(value, ['userId', 'displayName']);
  return { userId: sqlId(row.userId), displayName: text(row.displayName, 255) };
}
export function validateOverview(value: unknown, expectedId?: number): CaseOverview {
  const row = object(value, ['caseId', 'caseNumber', 'caseName', 'status', 'practiceArea', 'responsibleAttorney', 'primaryLegalAssistant', 'updatedAt']);
  const caseId = sqlId(row.caseId);
  if (expectedId !== undefined && caseId !== expectedId) return fail();
  return { caseId, caseNumber: nullable(row.caseNumber, v => text(v, 200)), caseName: text(row.caseName, 255),
    status: nullable(row.status, v => {
      const status = object(v, ['id', 'name', 'color']);
      const color = nullable(status.color, v => text(v, 7));
      if (color !== null && !/^#[0-9a-fA-F]{6}$/.test(color)) return fail();
      return { id: sqlId(status.id), name: text(status.name, 255), color };
    }),
    practiceArea: nullable(row.practiceArea, v => {
      const area = object(v, ['id', 'name']); return { id: sqlId(area.id), name: text(area.name, 255) };
    }),
    responsibleAttorney: nullable(row.responsibleAttorney, user), primaryLegalAssistant: nullable(row.primaryLegalAssistant, user),
    updatedAt: nullable(row.updatedAt, localTimestamp),
  };
}
export function validatePage(value: unknown, expectedPage: number): CasePage {
  const row = object(value, ['items', 'page', 'size', 'hasMore']);
  if (!Number.isInteger(row.page) || row.page !== expectedPage || expectedPage < 0 || expectedPage > 100
      || row.size !== 25 || typeof row.hasMore !== 'boolean' || !Array.isArray(row.items)
      || row.items.length > 25 || (row.hasMore && row.items.length !== 25)) return fail();
  const items = row.items.map(v => validateOverview(v));
  if (new Set(items.map(v => v.caseId)).size !== items.length) return fail();
  return { items, page: expectedPage, size: 25, hasMore: row.hasMore };
}
