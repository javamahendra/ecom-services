import { request } from "../api";

export function registerUser(user) {
  return request("/api/users", { method: "POST", body: user });
}

export function getUsers(token) {
  return request("/api/users", { token });
}

export function getUserProfile(id, token) {
  return request(`/api/users/${encodeURIComponent(id)}`, { token });
}

export function createUser(user, token) {
  return request("/api/users/admin", { method: "POST", token, body: user });
}

export function updateUserRole(id, role, token) {
  return request(`/api/users/${encodeURIComponent(id)}/role`, {
    method: "PUT",
    token,
    body: { role },
  });
}

export function updateUserStatus(id, enabled, token) {
  return request(`/api/users/${encodeURIComponent(id)}/status`, {
    method: "PUT",
    token,
    body: { enabled },
  });
}
