import { request } from "../api";

export function getOrders(page, size, token) {
  return request(`/api/orders?page=${page}&size=${size}`, { token });
}

export function getOrderPage(query, token) {
  return request(`/api/orders?${query}`, { token });
}

export function getOrderCount(token) {
  return request("/api/orders/count", { token });
}

export function getOrder(id, token) {
  return request(`/api/orders/${encodeURIComponent(id)}`, { token });
}

export function cancelOrder(id, token) {
  return request(`/api/orders/${encodeURIComponent(id)}/cancel`, { method: "POST", token });
}

export function createOrder(order, token) {
  return request("/api/orders", { method: "POST", token, body: order });
}

export function getDispatches(query, token) {
  return request(`/api/dispatches?${query}`, { token });
}

export function getDispatchCount(token) {
  return request("/api/dispatches/count", { token });
}

export function syncDispatches(token) {
  return request("/api/orders/dispatches/sync", { method: "POST", token });
}

export function updateDispatchStatus(orderId, update, token) {
  return request(`/api/dispatches/${encodeURIComponent(orderId)}/status`, {
    method: "PATCH",
    token,
    body: update,
  });
}
