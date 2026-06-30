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


export interface CaseStatusSetting {
  id: number;
  name: string | null;
  closed: boolean;
  sortOrder: number | null;
  color: string | null;
  lifecycleKey: string | null;
  systemKey: string | null;
  shaleClientId: number | null;
}

export interface PracticeAreaSetting {
  id: number;
  name: string | null;
  color: string | null;
  active: boolean;
  deleted: boolean;
  systemKey: string | null;
  shaleClientId: number | null;
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
  assignedUserId?: number | null;
  assignedUserDisplayName?: string | null;
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

export interface UpdateOrganizationDetailsRequest {
  name: string;
  phone?: string | null;
  fax?: string | null;
  email?: string | null;
  website?: string | null;
  address1?: string | null;
  address2?: string | null;
  city?: string | null;
  state?: string | null;
  postalCode?: string | null;
  country?: string | null;
  notes?: string | null;
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
  name: string | null;
  firstName: string | null;
  lastName: string | null;
  displayName: string | null;
  email: string | null;
  phone: string | null;
  addressHome: string | null;
  dateOfBirth: string | null;
  condition: string | null;
  deceased: boolean;
  client: boolean;
}

export interface UpdateContactDetailsRequest {
  name: string | null;
  firstName: string | null;
  lastName: string | null;
  email: string | null;
  phone: string | null;
  addressHome: string | null;
  dateOfBirth: string | null;
  condition: string | null;
  deceased: boolean;
}

export interface CaseUpdate {
  id: number;
  caseId: number;
  noteText: string;
  createdAt: string | null;
  updatedAt: string | null;
  createdByUserId: number | null;
  createdByDisplayName: string;
}

export interface CaseStatusHistoryItem {
  caseStatusId: number;
  statusId: number;
  statusName: string;
  color: string | null;
  lifecycleKey: string | null;
  systemKey: string | null;
  closed: boolean;
  notes: string | null;
  effectiveDate: string | null;
  endDate: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  primary: boolean;
  current: boolean;
}

export interface CaseRelatedContact {
  id: number;
  displayName: string | null;
  roleId: number | null;
  roleName: string | null;
  side: string | null;
  primary: boolean;
  email: string | null;
  phone: string | null;
}

export interface CaseDetail {
  caseId: number;
  caseNumber: string | null;
  caseName: string;
  description: string;
  caseStatus: string;
  responsibleAttorney: string | null;
  practiceAreaId: number | null;
  callerDate: string | null;
  dateOfInjury: string | null;
  statuteOfLimitations: string | null;
  tortNoticeDeadline: string | null;
  summary: string;
  rowVer: string;
  relatedContacts: CaseRelatedContact[];
  statusHistory: CaseStatusHistoryItem[];
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


export interface UpdateCaseCoreDetailsPayload {
  caseName: string;
  description: string;
  dateOfInjury?: string | null;
  statuteOfLimitations?: string | null;
  tortNoticeDeadline?: string | null;
  summary: string;
  expectedRowVer: string;
}

export async function updateCaseCoreDetails(accessToken: string, caseId: number, payload: UpdateCaseCoreDetailsPayload): Promise<CaseDetail> {
  const response = await fetch(`${apiBaseUrl()}/api/cases/${caseId}/core-details`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(payload),
  });

  if (response.status === 404) {
    throw new ApiError('Shale could not find that case.', response.status);
  }

  if (response.status === 409) {
    throw new ApiError('This case was changed by someone else. Refresh and try again.', response.status);
  }

  if (!response.ok) {
    throw new ApiError('Shale could not save the case details.', response.status);
  }

  return response.json() as Promise<CaseDetail>;
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

export interface CreateCaseTaskPayload {
  title: string;
  description?: string;
  dueDate?: string;
}

export async function listCaseTasks(accessToken: string, caseId: number): Promise<CaseTaskListItem[]> {
  const response = await fetch(`${apiBaseUrl()}/api/cases/${caseId}/tasks`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new ApiError('Shale could not load the case tasks.', response.status);
  }

  return response.json() as Promise<CaseTaskListItem[]>;
}

export async function createCaseTask(accessToken: string, caseId: number, payload: CreateCaseTaskPayload): Promise<CaseTaskListItem[]> {
  const response = await fetch(`${apiBaseUrl()}/api/cases/${caseId}/tasks`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new ApiError('Shale could not create the case task.', response.status);
  }

  return response.json() as Promise<CaseTaskListItem[]>;
}

export async function addCaseUpdate(accessToken: string, caseId: number, noteText: string): Promise<CaseUpdate[]> {
  const response = await fetch(`${apiBaseUrl()}/api/cases/${caseId}/updates`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ noteText }),
  });

  if (!response.ok) {
    throw new ApiError('Shale could not add the case update.', response.status);
  }

  return response.json() as Promise<CaseUpdate[]>;
}

export async function listCaseUpdates(accessToken: string, caseId: number): Promise<CaseUpdate[]> {
  const response = await fetch(`${apiBaseUrl()}/api/cases/${caseId}/updates`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new ApiError('Shale could not load the case updates.', response.status);
  }

  return response.json() as Promise<CaseUpdate[]>;
}

export async function completeTask(accessToken: string, taskId: number): Promise<TaskDetail> {
  const response = await fetch(`${apiBaseUrl()}/api/tasks/${taskId}/complete`, {
    method: 'PATCH',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (response.status === 404) {
    throw new ApiError('Shale could not find that task.', response.status);
  }

  if (!response.ok) {
    throw new ApiError('Shale could not complete the task.', response.status);
  }

  return response.json() as Promise<TaskDetail>;
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

export async function updateContactDetails(accessToken: string, contactId: number, request: UpdateContactDetailsRequest): Promise<ContactDetail> {
  const response = await fetch(`${apiBaseUrl()}/api/contacts/${contactId}`, {
    method: 'PATCH',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(request),
  });

  if (response.status === 400) {
    throw new ApiError('Check the contact details and try again.', response.status);
  }

  if (response.status === 404) {
    throw new ApiError('Shale could not find that contact.', response.status);
  }

  if (!response.ok) {
    throw new ApiError('Shale could not save the contact detail.', response.status);
  }

  return response.json() as Promise<ContactDetail>;
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

export async function updateOrganizationDetails(accessToken: string, organizationId: number, request: UpdateOrganizationDetailsRequest): Promise<OrganizationDetail> {
  const response = await fetch(`${apiBaseUrl()}/api/organizations/${organizationId}`, {
    method: 'PATCH',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(request),
  });

  if (response.status === 400) {
    throw new ApiError('Check the organization details and try again.', response.status);
  }

  if (response.status === 404) {
    throw new ApiError('Shale could not find that organization.', response.status);
  }

  if (!response.ok) {
    throw new ApiError('Shale could not save the organization detail.', response.status);
  }

  return response.json() as Promise<OrganizationDetail>;
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


export async function listCaseStatusLookup(accessToken: string): Promise<CaseStatusSetting[]> {
  const response = await fetch(`${apiBaseUrl()}/api/lookups/case-statuses`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new ApiError('Shale could not load case status options.', response.status);
  }

  return response.json() as Promise<CaseStatusSetting[]>;
}

export async function updateCaseStatus(accessToken: string, caseId: number, statusId: number): Promise<CaseDetail> {
  const response = await fetch(`${apiBaseUrl()}/api/cases/${caseId}/status`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ statusId }),
  });

  if (response.status === 400) {
    throw new ApiError('Choose a valid case status before saving.', response.status);
  }

  if (response.status === 404) {
    throw new ApiError('Shale could not find that case.', response.status);
  }

  if (!response.ok) {
    throw new ApiError('Shale could not save the case status.', response.status);
  }

  return response.json() as Promise<CaseDetail>;
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


export async function listCaseStatusSettings(accessToken: string): Promise<CaseStatusSetting[]> {
  const response = await fetch(`${apiBaseUrl()}/api/settings/case-statuses`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new ApiError('Shale could not load case status settings.', response.status);
  }

  return response.json() as Promise<CaseStatusSetting[]>;
}

export async function listPracticeAreaSettings(accessToken: string): Promise<PracticeAreaSetting[]> {
  const response = await fetch(`${apiBaseUrl()}/api/settings/practice-areas`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new ApiError('Shale could not load practice area settings.', response.status);
  }

  return response.json() as Promise<PracticeAreaSetting[]>;
}
