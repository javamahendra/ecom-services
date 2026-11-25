import axios from "axios";
import { getTokenClaims } from "./auth/token";
import { isSessionRecentlyActive } from "./auth/sessionActivity";
import { clearUser, setUser } from "./store/slices/authSlice";
import { store } from "./store/store";

const API_URL = (import.meta.env.VITE_API_URL || "").replace(/\/+$/, "");
let refreshPromise;
const inFlightGetRequests = new Map();

const apiClient = axios.create({
  baseURL: API_URL,
  headers: { "Content-Type": "application/json" },
});

function toRequestError(error) {
  if (axios.isAxiosError(error)) {
    const result = error.response?.data;
    if (!error.response) {
      return new Error("Cannot connect to the API gateway. Check that the services are running and try again.");
    }
    const message = typeof result === "string"
      ? result
      : result?.message || result?.detail || result?.error_description || result?.error;
    return new Error(message || `Request failed (${error.response.status || "network error"})`);
  }
  return error;
}

function isUnauthorized(error) {
  return axios.isAxiosError(error) && error.response?.status === 401;
}

function isTokenExpiring(token) {
  const expiresAt = Number(getTokenClaims(token).exp);
  return Number.isFinite(expiresAt) && expiresAt * 1000 <= Date.now() + 30_000;
}

async function refreshAccessToken(observedToken) {
  const auth = store.getState().auth;
  if (!isSessionRecentlyActive()) {
    store.dispatch(clearUser());
    throw new Error("Your session expired after being inactive. Please sign in again.");
  }
  if (!auth?.refreshToken) {
    store.dispatch(clearUser());
    throw new Error("Your session has expired. Please sign in again.");
  }
  if (auth.token !== observedToken) {
    if (auth.token && getTokenClaims(auth.token).sub === getTokenClaims(observedToken).sub) return auth.token;
    throw new Error("Your session changed. Please sign in again.");
  }

  if (!refreshPromise) {
    const refreshToken = auth.refreshToken;
    refreshPromise = axios.post(`${API_URL}/api/auth/refresh`, { refreshToken }, {
      headers: { "Content-Type": "application/json" },
    }).then(({ data }) => {
      const token = data?.access_token || data?.accessToken;
      if (!token) throw new Error("The server did not return a refreshed access token.");

      const latestAuth = store.getState().auth;
      if (latestAuth?.token !== observedToken) {
        if (latestAuth?.token && getTokenClaims(latestAuth.token).sub === getTokenClaims(observedToken).sub) {
          return latestAuth.token;
        }
        throw new Error("Your session changed. Please sign in again.");
      }
      store.dispatch(setUser({
        ...latestAuth,
        token,
        refreshToken: data.refresh_token || data.refreshToken || refreshToken,
      }));
      return token;
    }).catch((error) => {
      const status = axios.isAxiosError(error) ? error.response?.status : undefined;
      if (store.getState().auth?.token === observedToken && (status === 400 || status === 401)) {
        store.dispatch(clearUser());
      }
      throw toRequestError(error);
    }).finally(() => {
      refreshPromise = null;
    });
  }

  return refreshPromise;
}

function makeRequestConfig(path, requestOptions, body, headers, token) {
  return {
    url: path,
    ...requestOptions,
    data: body,
    headers: {
      ...headers,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  };
}

export function request(path, options = {}) {
  if ((options.method || "GET").toUpperCase() !== "GET") {
    return performRequest(path, options);
  }

  const currentToken = store.getState().auth?.token;
  const token = options.token ? (currentToken || options.token) : undefined;
  const key = JSON.stringify([path, token]);
  const existingRequest = inFlightGetRequests.get(key);
  if (existingRequest) return existingRequest;

  const pendingRequest = performRequest(path, options);
  inFlightGetRequests.set(key, pendingRequest);
  pendingRequest.then(
    () => {
      if (inFlightGetRequests.get(key) === pendingRequest) inFlightGetRequests.delete(key);
    },
    () => {
      if (inFlightGetRequests.get(key) === pendingRequest) inFlightGetRequests.delete(key);
    },
  );
  return pendingRequest;
}

async function performRequest(path, options = {}) {
  const { token, body, headers, ...requestOptions } = options;
  const currentToken = store.getState().auth?.token;
  let activeToken = token ? (currentToken || token) : undefined;

  try {
    if (activeToken && isTokenExpiring(activeToken)) {
      activeToken = await refreshAccessToken(activeToken);
    }
    const response = await apiClient.request(
      makeRequestConfig(path, requestOptions, body, headers, activeToken),
    );
    return response.status === 204 ? null : response.data ?? null;
  } catch (error) {
    const refreshEndpoint = path === "/api/auth/refresh" || path === "/api/auth/login";
    if (activeToken && !refreshEndpoint && isUnauthorized(error)) {
      const refreshedToken = await refreshAccessToken(activeToken);
      try {
        const response = await apiClient.request(
          makeRequestConfig(path, requestOptions, body, headers, refreshedToken),
        );
        return response.status === 204 ? null : response.data ?? null;
      } catch (retryError) {
        if (isUnauthorized(retryError) && store.getState().auth?.token === refreshedToken) {
          store.dispatch(clearUser());
        }
        throw toRequestError(retryError);
      }
    }
    throw toRequestError(error);
  }
}

export function loginUser(credentials) {
  return request("/api/auth/login", { method: "POST", body: credentials });
}

export function logoutUser(refreshToken) {
  return request("/api/auth/logout", { method: "POST", body: { refreshToken } });
}

export function normalizeProduct(product) {
  const variant = product.variants?.[0];
  return {
    ...product,
    id: product.id,
    price: Number(product.price || 0),
    offerPrice: product.offerPrice == null ? null : Number(product.offerPrice),
    image: variant?.imageUrls?.[0] || product.image || "",
    variantId: variant?.id || variant?.variantId || "default",
    color: variant?.color || product.baseColor || "",
  };
}
