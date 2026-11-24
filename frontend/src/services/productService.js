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

export function importProductStock(file, token, onUploadProgress) {
  const body = new FormData();
  body.append("file", file);
  return request("/api/products/stock-import", {
    method: "POST",
    token,
    body,
    onUploadProgress,
    headers: { "Content-Type": "multipart/form-data" },
  });
}

export function getStockImportHistory(page = 0, token) {
  const params = new URLSearchParams({ page: String(page), size: "10" });
  return request(`/api/products/stock-imports?${params}`, { token });
}
