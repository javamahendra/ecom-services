import { request } from "../api";

export function getInventory(query, token) {
  return request(`/api/inventory?${query}`, { token });
}

export function updateInventory(item, token) {
  return request("/api/inventory", { method: "PUT", token, body: item });
}

export function syncCatalogInventory(token) {
  return request("/api/products/inventory/sync", { method: "POST", token });
}

export function syncCatalogMetadata(token) {
  return request("/api/products/inventory/metadata-sync", { method: "POST", token });
}

export function syncInventoryVariantToProduct(productId, variantId, token) {
  const params = new URLSearchParams({ productId, variantId });
  return request(`/api/inventory/sync-product?${params}`, { method: "POST", token });
}
