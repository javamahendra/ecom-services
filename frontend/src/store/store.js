import { configureStore } from "@reduxjs/toolkit";
import authReducer from "./slices/authSlice";
import cartReducer from "./slices/cartSlice";

export const store = configureStore({
  reducer: {
    cart: cartReducer,
    auth: authReducer,
  },
});

store.subscribe(() => {
  const { cart, auth } = store.getState();
  try {
    localStorage.setItem("form-field-cart", JSON.stringify(cart));
    if (auth) localStorage.setItem("form-field-user", JSON.stringify(auth));
    else localStorage.removeItem("form-field-user");
  } catch {
    // Keep in-memory Redux state available if browser storage is disabled or full.
  }
});
