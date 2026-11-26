const ORDER_STATUS_LABELS = {
  PENDING_INVENTORY: "Awaiting inventory confirmation",
  CONFIRMED: "Order confirmed",
  PARTIALLY_CONFIRMED: "Partially confirmed",
  REJECTED: "Inventory unavailable",
  CANCELLATION_PENDING: "Cancellation processing",
  CANCELLED: "Cancelled",
};

export function orderStatusLabel(status) {
  return ORDER_STATUS_LABELS[status] || status?.replaceAll("_", " ") || "Processing";
}

export function isOrderProcessing(status) {
  return status === "PENDING_INVENTORY" || status === "CANCELLATION_PENDING";
}
