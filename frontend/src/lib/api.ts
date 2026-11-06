import axios, { AxiosError, type AxiosRequestConfig } from "axios";
import type { ApiErrorBody, ApiFailure } from "@/types/api";

export const api = axios.create({
  baseURL: "/api",
  withCredentials: true,
  headers: { "Content-Type": "application/json" },
});

let csrfToken: string | null = null;
let csrfRequest: Promise<string> | null = null;

async function getCsrfToken(): Promise<string> {
  if (csrfToken) return csrfToken;
  if (!csrfRequest) {
    csrfRequest = api
      .get<{ token: string }>("/auth/csrf")
      .then(({ data }) => {
        csrfToken = data.token;
        return data.token;
      })
      .finally(() => {
        csrfRequest = null;
      });
  }
  return csrfRequest;
}

api.interceptors.request.use(async (config) => {
  const method = config.method?.toLowerCase();
  if (method && !["get", "head", "options"].includes(method)) {
    config.headers.set("X-XSRF-TOKEN", await getCsrfToken());
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorBody>) => {
    if (error.response?.status === 401
      && !["/auth/login", "/auth/me"].includes(error.config?.url ?? "")
      && typeof window !== "undefined") {
      csrfToken = null;
      window.dispatchEvent(new CustomEvent("stockly:unauthorized"));
    }
    return Promise.reject(error);
  },
);

export function apiError(error: unknown): ApiFailure {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    const body = error.response?.data;
    const status = error.response?.status;
    const statusMessage = status === 401
      ? "Your session has expired. Please sign in again."
      : status === 403
        ? "You do not have permission to perform this action."
        : status === 404
          ? "The requested record could not be found."
          : status === 409
            ? "This operation conflicts with current data. Refresh and retry."
            : status && status >= 500
              ? "The server encountered an error. Please try again."
              : undefined;
    return {
      status,
      message: body?.message ?? statusMessage ?? error.message ?? "Network request failed.",
    };
  }
  return { message: error instanceof Error ? error.message : "Unexpected error." };
}

export async function downloadFile(
  path: string,
  filename: string,
  config?: AxiosRequestConfig,
): Promise<void> {
  const { data } = await api.get<Blob>(path, { ...config, responseType: "blob" });
  const url = URL.createObjectURL(data);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
