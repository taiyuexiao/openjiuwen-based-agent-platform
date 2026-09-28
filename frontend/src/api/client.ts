import { message } from 'antd';
import type { ApiResponse } from '../types';

const TOKEN_KEY = 'agentops_token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

/**
 * 统一 API 封装：全部 POST /v1/api/...，自动携带 Bearer token，
 * code 非 0 时 message.error 提示并抛错；401 时清理 token 并跳转登录页。
 */
export async function api<T>(path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  let resp: Response;
  try {
    resp = await fetch(path, {
      method: 'POST',
      headers,
      body: body === undefined ? '{}' : JSON.stringify(body),
    });
  } catch {
    message.error('网络错误，请确认后端服务已启动（localhost:8080）');
    throw new Error('network error');
  }
  return handleResp(resp);
}

/** multipart 文件上传（资产上传接口） */
export async function apiUpload<T>(path: string, formData: FormData): Promise<T> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  let resp: Response;
  try {
    resp = await fetch(path, { method: 'POST', headers, body: formData });
  } catch {
    message.error('网络错误，请确认后端服务已启动');
    throw new Error('network error');
  }
  return handleResp(resp);
}

async function handleResp<T>(resp: Response): Promise<T> {
  if (resp.status === 401) {
    clearToken();
    message.error('未认证或登录已失效，请重新登录');
    if (window.location.pathname !== '/login') {
      window.location.href = '/login';
    }
    throw new Error('unauthorized');
  }
  const json = (await resp.json()) as ApiResponse<T>;
  if (json.code !== 0) {
    message.error(json.message || `请求失败（code=${json.code}）`);
    throw new Error(json.message || `code=${json.code}`);
  }
  return json.data;
}
