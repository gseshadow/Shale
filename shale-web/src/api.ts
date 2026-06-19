const DEFAULT_API_BASE_URL = 'https://shale-api.azurewebsites.net';

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

export class LoginError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
    this.name = 'LoginError';
  }
}

export function apiBaseUrl(): string {
  const configured = import.meta.env.VITE_SHALE_API_BASE_URL?.trim();
  return (configured || DEFAULT_API_BASE_URL).replace(/\/+$/, '');
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
    throw new LoginError('The email or password was not accepted by Shale.', response.status);
  }

  return response.json() as Promise<LoginResponse>;
}
