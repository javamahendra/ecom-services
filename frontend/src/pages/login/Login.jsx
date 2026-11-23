import { ArrowRight } from "lucide-react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useDispatch } from "react-redux";
import { toast } from "react-toastify";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { loginUser } from "../../api";
import { getRealmRoles, getTokenClaims } from "../../auth/token";
import AuthShell from "../../components/auth/AuthShell";
import PasswordInput from "../../components/auth/PasswordInput";
import { setUser } from "../../store/slices/authSlice";
import { markSessionActivity } from "../../auth/sessionActivity";

const loginSchema = z.object({
  username: z.string().trim().min(1, "Enter your email or username."),
  password: z.string().min(8, "Enter your password."),
});

export default function LoginPage() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: "", password: "" },
  });

  const submitLogin = async ({ username, password }) => {
    try {
      const result = await loginUser({ username, password });
      const token = result.access_token || result.accessToken;
      if (!token) throw new Error("The server did not return an access token.");

      const claims = getTokenClaims(token);
      const roles = getRealmRoles(claims);
      console.info("Signed-in user:", {
        id: claims.sub,
        username: claims.preferred_username,
        name: claims.name,
        email: claims.email,
        roles,
      });
      markSessionActivity();
      dispatch(setUser({
        token,
        refreshToken: result.refresh_token || result.refreshToken,
        id: claims.sub,
        roles,
        email: claims.email || (username.includes("@") ? username : ""),
        name: claims.name || claims.preferred_username || (username.includes("@") ? username.split("@")[0] : username),
      }));
      toast.success("Welcome back.");
      const defaultRoute = roles.includes("admin") ? "/admin" : roles.includes("manager") ? "/manager" : "/account";
      navigate(location.state?.from || defaultRoute, { replace: true });
    } catch (error) {
      toast.error(error.message || "Unable to sign in. Please try again.");
    }
  };

  return (
    <AuthShell eyebrow="Welcome back" title="Good to see you." description="Sign in to pick up where you left off.">
      <form onSubmit={handleSubmit(submitLogin)} noValidate className="mt-8 space-y-4">
        <label className="block text-xs text-[#5d655a]">
          Email or username
          <input type="text" autoComplete="username" {...register("username")} className="mt-2 h-11 w-full border border-[#dfe2d9] bg-white px-3 text-sm outline-none transition focus:border-[#71816d]" />
          {errors.username && <span className="mt-1 block text-xs text-red-700">{errors.username.message}</span>}
        </label>
        <PasswordInput registration={register("password")} autoComplete="current-password" error={errors.password?.message} />
        <button disabled={isSubmitting} className="mt-3 flex h-12 w-full items-center justify-center gap-2 bg-[#314638] text-xs font-medium uppercase tracking-[0.12em] text-white transition hover:bg-[#25372b] disabled:cursor-wait disabled:opacity-60">
          {isSubmitting ? "Please wait..." : "Sign in"} {!isSubmitting && <ArrowRight size={14} />}
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-[#81877c]">
        New to Form & Field? <Link to="/register" className="font-medium text-[#394c3c] underline decoration-[#b8c0b3] underline-offset-4">Create an account</Link>
      </p>
    </AuthShell>
  );
}
