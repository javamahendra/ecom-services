import { request } from "../api";

export function getProducts(query, token) {
  return request(`/api/products?${query}`, { token });
}

export function getProduct(id, token) {
  return request(`/api/products/${encodeURIComponent(id)}`, { token });
}

export function createProduct(product, token) {
  return request("/api/products", { method: "POST", token, body: product });
}

export function updateProduct(id, product, token) {
  return request(`/api/products/${encodeURIComponent(id)}`, { method: "PUT", token, body: product });
}

export function deleteProduct(id, token) {
  return request(`/api/products/${encodeURIComponent(id)}`, { method: "DELETE", token });
}
