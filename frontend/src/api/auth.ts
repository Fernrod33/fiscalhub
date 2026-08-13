import client from './client';
import type { AuthUser, LoginRequest, SignupRequest } from '../types';

export async function login(data: LoginRequest): Promise<AuthUser> {
  const res = await client.post<AuthUser>('/api/auth/login', data);
  return res.data;
}

export async function signup(data: SignupRequest): Promise<{ message: string }> {
  const res = await client.post<{ message: string }>('/api/auth/signup', data);
  return res.data;
}
