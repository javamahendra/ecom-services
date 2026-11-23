import { createSlice } from "@reduxjs/toolkit";

function readCart() {
  try {
    const stored = localStorage.getItem("form-field-cart");
    const cart = stored ? JSON.parse(stored) : [];
    return Array.isArray(cart)
      ? cart.map((item) => ({ ...item, quantity: Math.max(1, Number(item.quantity) || 1) }))
      : [];
  } catch {
    return [];
  }
}

const cartSlice = createSlice({
  name: "cart",
  initialState: readCart(),
  reducers: {
    addItem(state, action) {
      const item = action.payload;
      const existing = state.find((entry) => entry.id === item.id);
      const quantity = Math.max(1, Number(item.quantity) || 1);
      if (existing) existing.quantity = (Number(existing.quantity) || 1) + quantity;
      else state.push({ ...item, quantity });
    },
    setItemQuantity(state, action) {
      const item = action.payload;
      const quantity = Math.max(0, Number(item.quantity) || 0);
      const existing = state.find((entry) => entry.id === item.id);
      if (quantity === 0) return state.filter((entry) => entry.id !== item.id);
      if (existing) existing.quantity = quantity;
      else state.push({ ...item, quantity });
    },
    setQuantity(state, action) {
      const item = state.find((entry) => entry.id === action.payload.id);
      if (item) item.quantity = Math.max(1, Number(action.payload.quantity) || 1);
    },
    incrementItem(state, action) {
      const item = state.find((entry) => entry.id === action.payload);
      if (item) item.quantity = (Number(item.quantity) || 1) + 1;
    },
    decrementItem(state, action) {
      const item = state.find((entry) => entry.id === action.payload);
      if (item) item.quantity = Math.max(1, (Number(item.quantity) || 1) - 1);
    },
    removeItem(state, action) {
      return state.filter((item) => item.id !== action.payload);
    },
    clearCart: () => [],
  },
});

export const { addItem, setItemQuantity, setQuantity, incrementItem, decrementItem, removeItem, clearCart } = cartSlice.actions;
export default cartSlice.reducer;
