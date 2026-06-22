const DEFAULT_API_BASE_URL = 'https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net';
const ACCESS_TOKEN_STORAGE_KEY = 'shale-web.accessToken';

export interface AuthenticatedUser {
  authenticated: boolean;
  userId: number;
  shaleClientId: number;
  email: string | null;
  displayName: string | null;
  nameFirst: string | null;
  nameLast: string | null;
  isAdmin: boolean;
  isAttorney: boolean;
  initials: string | null;
  color: string | null;
}

export interface LoginResponse {
  authenticated: boolean;
  tokenType: 'Bearer' | string;
  accessToken: string;
  expiresInSeconds: number;
  user: AuthenticatedUser;
}

export interface CaseSearchResult {
  caseId: number;
  caseNumber: string;
  caseName: string;
  caseStatus: string;
  responsibleAttorney: string;
  practiceArea: string;
  intakeDate: string | null;
  incidentDate?: string | null;
  solDate?: string | null;
  client: string;
}

export interface CaseTaskListItem {
  id: number;
  caseId: number;
  caseName: string | null;
  title: string | null;
  priorityId: number | null;
  dueAt: string | null;
  completedAt: string | null;
}

export interface TaskDetail {
  id: number;
  shaleClientId: number;
  caseId: number;
  caseName: string | null;
  caseResponsibleAttorney: string | null;
  caseResponsibleAttorneyColor: string | null;
  caseNonEngagementLetterSent: boolean | null;
  title: string | null;
  description: string | null;
  dueAt: string | null;
  statusId: number | null;
  priorityId: number | null;
  completedAt: string | null;
  assignedUserId: number | null;
  assignedUserDisplayName: string | null;
  assignedUserColor: string | null;
  createdByDisplayName: string | null;
}


export interface TeamMemberSummary {
  id: number;
  firstName: string | null;
  lastName: string | null;
  displayName: string | null;
  email: string | null;
  phone: string | null;
  color: string | null;
  initials: string | null;
  admin: boolean;
  attorney: boolean;
}

export interface TeamMemberDetail extends TeamMemberSummary {
  shaleClientId: number;
}

export interface ContactSearchResult {
  id: number;
  displayName: string | null;
  email: string | null;
  phone: string | null;
}


export interface OrganizationSearchResult {
  id: number;
  name: string | null;
  organizationTypeId: number | null;
  organizationTypeName: string | null;
  phone: string | null;
  email: string | null;
  website: string | null;
  city: string | null;
  state: string | null;
}

export interface OrganizationRelatedCase {
  id: number;
  name: string | null;
  intakeDate: string | null;
  statuteOfLimitationsDate: string | null;
  responsibleAttorneyName: string | null;
  partyRoleName: string | null;
  side: string | null;
  primary: boolean;
  notes: string | null;
}

export interface OrganizationDetail {
  id: number;
  shaleClientId: number;
  organizationTypeId: number | null;
  organizationTypeName: string | null;
  name: string | null;
  phone: string | null;
  fax: string | null;
  email: string | null;
  website: string | null;
  address1: string | null;
  address2: string | null;
  city: string | null;
  state: string | null;
  postalCode: string | null;
  country: string | null;
  notes: string | null;
  relatedCases: OrganizationRelatedCase[];
}

export interface ContactDetail {
  id: number;
  shaleClientId: number;
  firstName: string | null;
  lastName: string | null;
  displayName: string | null;
  email: string | null;
  phone: string | null;
}

export interface CaseDetail {
  caseId: number;
  caseNumber: string | null;
  caseName: string;
  description: string;
  caseStatus: string;
  practiceAreaId: number | null;
  callerDate: string | null;
  dateOfInjury: string | null;
  statuteOfLimitations: string | null;
  tortNoticeDeadline: string | null;
  summary: string;
}

export class ApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
    this.name = 'ApiError';
  }
}

export function apiBaseUrl(): string {
  const configured = import.meta.env.VITE_SHALE_API_BASE_URL?.trim();
  return (configured || DEFAULT_API_BASE_URL).replace(/\/+$/, '');
}

export function storeAccessToken(accessToken: string): void {
  sessionStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, accessToken);
}

export function readAccessToken(): string | null {
  return sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
}

export function clearAccessToken(): void {
  sessionStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  const response = await fetch(`${apiBaseUrl()}/api/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify({ email, password }),
  });

  if (!response.ok) {
    throw new ApiError('The email or password was not accepted by Shale.', response.status);
  }

  return response.json() as Promise<LoginResponse>;
}

export async function getCurrentUser(accessToken: string): Promise<AuthenticatedUser> {
  const response = await fetch(`${apiBaseUrl()}/api/auth/me`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new ApiError('Shale could not verify the signed-in user.', response.status);
  }

  return response.json() as Promise<AuthenticatedUser>;
}

export async function logout(accessToken: string): Promise<void> {
  await fetch(`${apiBaseUrl()}/api/auth/logout`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });
}
export async function searchCases(accessToken: string, query: string): Promise<CaseSearchResult[]> {
  const response = await fetch(`${apiBaseUrl()}/api/cases/search?query=${encodeURIComponent(query)}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new ApiError('Shale could not search cases.', response.status);
  }

  return response.json() as Promise<CaseSearchResult[]>;
}


export async function getCaseDetail(accessToken: string, caseId: number): Promise<CaseDetail> {
  const response = await fetch(`${apiBaseUrl()}/api/cases/${caseId}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (response.status === 404) {
    throw new ApiError('Shale could not find that case.', response.status);
  }

  if (!response.ok) {
    throw new ApiError('Shale could not load the case detail.', response.status);
  }

  return response.json() as Promise<CaseDetail>;
}


export async function listAssignedCases(accessToken: string): Promise<CaseSearchResult[]> {
  const response = await fetch(`${apiBaseUrl()}/api/cases/assigned`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new ApiError('Shale could not load your cases.', response.status);
  }

  return response.json() as Promise<CaseSearchResult[]>;
}

export async function listAssignedTasks(accessToken: string): Promise<CaseTaskListItem[]> {
  const response = await fetch(`${apiBaseUrl()}/api/tasks/assigned`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new ApiError('Shale could not load your tasks.', response.status);
  }

  return response.json() as Promise<CaseTaskListItem[]>;
}

export async function getTaskDetail(accessToken: string, taskId: number): Promise<TaskDetail> {
  const response = await fetch(`${apiBaseUrl()}/api/tasks/${taskId}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (response.status === 404) {
    throw new ApiError('Shale could not find that task.', response.status);
  }

  if (!response.ok) {
    throw new ApiError('Shale could not load the task detail.', response.status);
  }

  return response.json() as Promise<TaskDetail>;
}


export async function searchContacts(accessToken: string, query: string): Promise<ContactSearchResult[]> {
  const response = await fetch(`${apiBaseUrl()}/api/contacts/search?query=${encodeURIComponent(query)}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new ApiError('Shale could not search contacts.', response.status);
  }

  return response.json() as Promise<ContactSearchResult[]>;
}

export async function getContactDetail(accessToken: string, contactId: number): Promise<ContactDetail> {
  const response = await fetch(`${apiBaseUrl()}/api/contacts/${contactId}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (response.status === 404) {
    throw new ApiError('Shale could not find that contact.', response.status);
  }

  if (!response.ok) {
    throw new ApiError('Shale could not load the contact detail.', response.status);
  }

  return response.json() as Promise<ContactDetail>;
}

export async function searchOrganizations(accessToken: string, query: string): Promise<OrganizationSearchResult[]> {
  const response = await fetch(`${apiBaseUrl()}/api/organizations/search?query=${encodeURIComponent(query)}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new ApiError('Shale could not search organizations.', response.status);
  }

  return response.json() as Promise<OrganizationSearchResult[]>;
}

export async function getOrganizationDetail(accessToken: string, organizationId: number): Promise<OrganizationDetail> {
  const response = await fetch(`${apiBaseUrl()}/api/organizations/${organizationId}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (response.status === 404) {
    throw new ApiError('Shale could not find that organization.', response.status);
  }

  if (!response.ok) {
    throw new ApiError('Shale could not load the organization detail.', response.status);
  }

  return response.json() as Promise<OrganizationDetail>;
}


export async function listTeamMembers(accessToken: string): Promise<TeamMemberSummary[]> {
  const response = await fetch(`${apiBaseUrl()}/api/users`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new ApiError('Shale could not load the team directory.', response.status);
  }

  return response.json() as Promise<TeamMemberSummary[]>;
}

export async function getTeamMemberDetail(accessToken: string, userId: number): Promise<TeamMemberDetail> {
  const response = await fetch(`${apiBaseUrl()}/api/users/${userId}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (response.status === 404) {
    throw new ApiError('Shale could not find that team member.', response.status);
  }

  if (!response.ok) {
    throw new ApiError('Shale could not load the team member detail.', response.status);
  }

  return response.json() as Promise<TeamMemberDetail>;
}
