export function getTokenClaims(token) {
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const bytes = Uint8Array.from(atob(payload), (character) => character.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return {};
  }
}

export function getRealmRoles(claims) {
  const roles = Array.isArray(claims.realm_access?.roles) ? claims.realm_access.roles : [];
  return roles.some((role) => ["admin", "manager", "user"].includes(role)) ? roles : [...roles, "user"];
}
