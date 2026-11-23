import { createSlice } from "@reduxjs/toolkit";

function readUser() {
  try {
    const stored = localStorage.getItem("form-field-user");
    return stored ? JSON.parse(stored) : null;
  } catch {
    return null;
  }
}

const authSlice = createSlice({
  name: "auth",
  initialState: readUser(),
  reducers: {
    setUser: (_state, action) => action.payload,
    clearUser: () => null,
  },
});

export const { setUser, clearUser } = authSlice.actions;
export default authSlice.reducer;
