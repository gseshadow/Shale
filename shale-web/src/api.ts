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
