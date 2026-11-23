import { request } from "../api";

export function getBanner(token) {
  return request("/api/banner", { token });
}

export function updateBanner(banner, token) {
  return request("/api/banner", { method: "PUT", token, body: banner });
}
