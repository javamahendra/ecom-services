import { ArrowRight } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { toast } from "react-toastify";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { registerUser } from "../../services/userService";
import AuthShell from "../../components/auth/AuthShell";
import PasswordInput from "../../components/auth/PasswordInput";

const registrationSchema = z.object({
  firstName: z.string().trim().min(1, "Enter your first name.").max(100, "First name must be 100 characters or fewer."),
  lastName: z.string().trim().min(1, "Enter your last name.").max(100, "Last name must be 100 characters or fewer."),
  username: z.string().trim().min(1, "Choose a username.").max(100, "Username must be 100 characters or fewer."),
  email: z.string().trim().email("Enter a valid email address.").max(254, "Email must be 254 characters or fewer."),
  phone: z.union([z.literal(""), z.string().regex(/^\+?[0-9 ()-]{7,30}$/, "Enter a valid phone number.")]),
  password: z.string().min(12, "Password must be at least 12 characters.").max(128, "Password must be 128 characters or fewer."),
});

export default function RegisterPage() {
  const navigate = useNavigate();
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(registrationSchema),
    defaultValues: { firstName: "", lastName: "", username: "", email: "", phone: "", password: "" },
  });

  const submitRegistration = async (form) => {
    try {
      await registerUser({
        username: form.username,
        email: form.email,
        password: form.password,
        firstName: form.firstName,
        lastName: form.lastName,
        phone: form.phone,
      });
      toast.success("Your account is ready. Sign in to continue.");
      navigate("/login", { replace: true });
    } catch (error) {
      toast.error(error.message || "Unable to create your account. Please try again.");
    }
  };

  return (
    <AuthShell eyebrow="Join us" title="Create your account." description="Save your details and keep your favorites close.">
      <form onSubmit={handleSubmit(submitRegistration)} noValidate className="mt-8 space-y-4">
        <div className="grid grid-cols-2 gap-3">
          <label className="text-xs text-[#5d655a]">
            First name
            <input {...register("firstName")} autoComplete="given-name" className="mt-2 h-11 w-full border border-[#dfe2d9] bg-white px-3 text-sm outline-none transition focus:border-[#71816d]" />
            {errors.firstName && <span className="mt-1 block text-xs text-red-700">{errors.firstName.message}</span>}
          </label>
          <label className="text-xs text-[#5d655a]">
            Last name
            <input {...register("lastName")} autoComplete="family-name" className="mt-2 h-11 w-full border border-[#dfe2d9] bg-white px-3 text-sm outline-none transition focus:border-[#71816d]" />
            {errors.lastName && <span className="mt-1 block text-xs text-red-700">{errors.lastName.message}</span>}
          </label>
        </div>
        <label className="block text-xs text-[#5d655a]">
          Username
          <input {...register("username")} autoComplete="username" className="mt-2 h-11 w-full border border-[#dfe2d9] bg-white px-3 text-sm outline-none transition focus:border-[#71816d]" />
          {errors.username && <span className="mt-1 block text-xs text-red-700">{errors.username.message}</span>}
        </label>
        <label className="block text-xs text-[#5d655a]">
          Email address
          <input {...register("email")} type="email" autoComplete="email" className="mt-2 h-11 w-full border border-[#dfe2d9] bg-white px-3 text-sm outline-none transition focus:border-[#71816d]" />
          {errors.email && <span className="mt-1 block text-xs text-red-700">{errors.email.message}</span>}
        </label>
        <label className="block text-xs text-[#5d655a]">
          Phone number <span className="text-[#9ba095]">(optional)</span>
          <input {...register("phone")} type="tel" autoComplete="tel" className="mt-2 h-11 w-full border border-[#dfe2d9] bg-white px-3 text-sm outline-none transition focus:border-[#71816d]" />
          {errors.phone && <span className="mt-1 block text-xs text-red-700">{errors.phone.message}</span>}
        </label>
        <PasswordInput registration={register("password")} autoComplete="new-password" minLength={12} error={errors.password?.message} />
        <button disabled={isSubmitting} className="mt-3 flex h-12 w-full items-center justify-center gap-2 bg-[#314638] text-xs font-medium uppercase tracking-[0.12em] text-white transition hover:bg-[#25372b] disabled:cursor-wait disabled:opacity-60">
          {isSubmitting ? "Please wait..." : "Create account"} {!isSubmitting && <ArrowRight size={14} />}
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-[#81877c]">
        Already have an account? <Link to="/login" className="font-medium text-[#394c3c] underline decoration-[#b8c0b3] underline-offset-4">Sign in</Link>
      </p>
    </AuthShell>
  );
}
