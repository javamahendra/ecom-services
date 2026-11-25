import { useCallback, useEffect, useState } from "react";
import { useSelector } from "react-redux";
import { toast } from "react-toastify";
import LoadingState from "../../../components/LoadingState";
import { createUser as createUserRequest, getUsers, updateUserRole, updateUserStatus } from "../../../services/userService";

const managedRoles = ["admin", "manager", "user"];

export function UserManagement() {

  const { token, id: currentUserId } = useSelector((state) => state.auth);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [updatingIds, setUpdatingIds] = useState([]);
  const [form, setForm] = useState({
    username: "", email: "", firstName: "", lastName: "", phone: "", password: "", role: "user",
  });

  const loadUsers = useCallback(async () => {
    try {
      const result = await getUsers(token);
      setUsers(Array.isArray(result) ? result : []);
    } catch (error) {
      toast.error(error.message || "Could not load users.");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { loadUsers(); }, [loadUsers]);

  const updateForm = (event) => {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }));
  };

  const createUser = async (event) => {
    event.preventDefault();
    setSaving(true);
    try {
      await createUserRequest({
          password: form.password,
          role: form.role,
          profile: {
            username: form.username,
            email: form.email,
            firstName: form.firstName,
            lastName: form.lastName,
            phone: form.phone || null,
          },
        }, token);
      setForm({ username: "", email: "", firstName: "", lastName: "", phone: "", password: "", role: "user" });
      toast.success("User created.");
      await loadUsers();
    } catch (error) {
      toast.error(error.message || "Could not create user.");
    } finally {
      setSaving(false);
    }
  };

  const changeRole = async (user, role) => {
    if (user.roles?.includes(role)) return;
    try {
      const updated = await updateUserRole(user.id, role, token);
      setUsers((current) => current.map((entry) => entry.id === updated.id ? updated : entry));
      toast.success(`Role changed to ${role}.`);
    } catch (error) {
      toast.error(error.message || "Could not change user role.");
    }
  };

  const changeStatus = async (user) => {
    const enabled = !user.enabled;
    setUpdatingIds((current) => [...current, user.id]);
    try {
      const updated = await updateUserStatus(user.id, enabled, token);
      setUsers((current) => current.map((entry) => entry.id === updated.id ? updated : entry));
      toast.success(enabled ? "User enabled." : "User disabled.");
    } catch (error) {
      toast.error(error.message || "Could not update user status.");
    } finally {
      setUpdatingIds((current) => current.filter((id) => id !== user.id));
    }
  };

  return (
    <section className="mt-8 border border-[#e4e6de] bg-white p-5 sm:p-6">
      <h2 className="text-lg font-medium text-[#354237]">User access</h2>
      <p className="mt-1 text-xs text-[#858a80]">Create accounts and assign admin, manager, or user access.</p>
      <form onSubmit={createUser} className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {[
          ["username", "Username", "text"],
          ["email", "Email", "email"],
          ["firstName", "First name", "text"],
          ["lastName", "Last name", "text"],
          ["phone", "Phone (optional)", "tel"],
          ["password", "Temporary password (min. 12 characters)", "password"],
        ].map(([name, label, type]) => (
          <label key={name} className="text-xs text-[#5d655a]">{label}
            <input name={name} type={type} required={name !== "phone"} minLength={name === "password" ? 12 : undefined}
              maxLength={name === "password" ? 128 : name === "email" ? 254 : 100} value={form[name]}
              onChange={updateForm} className="mt-1.5 h-10 w-full border border-[#dfe2d9] bg-white px-3 text-sm outline-none focus:border-[#71816d]" />
          </label>
        ))}
        <label className="text-xs text-[#5d655a]">Role
          <select name="role" value={form.role} onChange={updateForm} className="mt-1.5 h-10 w-full border border-[#dfe2d9] bg-white px-3 text-sm">
            {managedRoles.map((role) => <option key={role} value={role}>{role}</option>)}
          </select>
        </label>
        <button disabled={saving} className="h-10 self-end bg-[#314638] px-5 text-xs font-medium uppercase tracking-[0.1em] text-white disabled:opacity-60">
          {saving ? "Creating…" : "Create user"}
        </button>
      </form>

      <h3 className="mt-8 text-sm font-medium text-[#354237]">Existing users</h3>
      {loading ? <LoadingState label="Loading users…" compact /> : users.length === 0 ? (
        <p className="mt-3 text-sm text-[#858a80]">No users found.</p>
      ) : (
        <div className="mt-3 overflow-x-auto border border-[#e4e6de]">
          <table className="w-full min-w-[620px] text-left text-sm">
            <thead className="border-b border-[#e4e6de] bg-[#fbfbf8] text-xs text-[#858a80]">
              <tr><th className="px-4 py-3 font-medium">User</th><th className="px-4 py-3 font-medium">Email</th><th className="px-4 py-3 font-medium">Role</th><th className="px-4 py-3 font-medium">Status</th></tr>
            </thead>
            <tbody>{users.map((user) => (
              <tr key={user.id} className="border-b border-[#eef0ea] last:border-0">
                <td className="px-4 py-3 font-medium text-[#354237]">{user.firstName} {user.lastName} <span className="font-normal text-[#858a80]">(@{user.username})</span></td>
                <td className="px-4 py-3 text-[#687064]">{user.email}</td>
                <td className="px-4 py-3">
                  <select aria-label={`Role for ${user.username}`} value={user.roles?.find((role) => managedRoles.includes(role)) || "user"}
                    onChange={(event) => changeRole(user, event.target.value)} className="h-9 border border-[#dfe2d9] bg-white px-2 text-sm">
                    {managedRoles.map((role) => <option key={role} value={role}>{role}</option>)}
                  </select>
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    <span className={user.enabled ? "text-xs font-medium text-emerald-700" : "text-xs font-medium text-red-700"}>
                      {user.enabled ? "Enabled" : "Disabled"}
                    </span>
                    <button type="button" disabled={updatingIds.includes(user.id) || user.id === currentUserId}
                      onClick={() => changeStatus(user)} aria-label={`${user.enabled ? "Disable" : "Enable"} ${user.username}`}
                      className="rounded border border-[#dfe2d9] px-2.5 py-1 text-xs text-[#52644f] hover:bg-[#f4f5f0] disabled:cursor-not-allowed disabled:opacity-50">
                      {updatingIds.includes(user.id) ? "Saving…" : user.enabled ? "Disable" : "Enable"}
                    </button>
                  </div>
                </td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
    </section>
  );
}
