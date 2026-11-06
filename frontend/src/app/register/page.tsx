"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowRight, Boxes, Eye, EyeOff, LockKeyhole, Mail, Phone, UserRound } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useRouter } from "next/navigation";
import { z } from "zod";
import { api, apiError } from "@/lib/api";
import { useAuth } from "@/context/AuthContext";
import { homePathForRole } from "@/lib/auth";

const passwordPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,72}$/;
const schema = z.object({
  firstName: z.string().trim().min(1, "Enter your first name").max(80),
  lastName: z.string().trim().min(1, "Enter your last name").max(80),
  email: z.string().trim().email("Enter a valid email address").max(160),
  mobileNumber: z.string().trim().regex(/^\+?[0-9 ()-]{7,24}$/, "Enter a valid mobile number"),
  username: z.string().trim().min(3, "Use at least 3 characters").max(60),
  password: z.string().regex(passwordPattern, "Use 10–72 characters with uppercase, lowercase, a number, and a symbol"),
  confirmPassword: z.string().min(1, "Confirm your password"),
  acceptTerms: z.boolean().refine(Boolean, "You must accept the terms and conditions"),
}).refine((values) => values.password === values.confirmPassword, {
  path: ["confirmPassword"],
  message: "Passwords do not match",
});
type RegistrationValues = z.infer<typeof schema>;

const initialValues: RegistrationValues = {
  firstName: "", lastName: "", email: "", mobileNumber: "", username: "",
  password: "", confirmPassword: "", acceptTerms: false,
};

export default function RegisterPage() {
  const { user, loading } = useAuth();
  const router = useRouter();
  const [showPassword, setShowPassword] = useState(false);
  const [submitError, setSubmitError] = useState("");
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<RegistrationValues>({
    resolver: zodResolver(schema),
    defaultValues: initialValues,
  });

  useEffect(() => {
    if (!loading && user) router.replace(homePathForRole(user.role));
  }, [loading, user, router]);

  const onSubmit = async (values: RegistrationValues) => {
    setSubmitError("");
    try {
      await api.post("/users", {
        username: values.username,
        email: values.email,
        password: values.password,
        firstName: values.firstName,
        lastName: values.lastName,
        mobileNumber: values.mobileNumber,
      });
      router.replace("/login?registered=1");
    } catch (requestError) {
      setSubmitError(apiError(requestError).message);
    }
  };

  if (loading || user) return <div className="loading"><span className="spinner" />Loading STOCKLY…</div>;
  return <main className="login-screen">
    <section className="login-art">
      <Brand dark />
      <div className="login-art-message">
        <div className="eyebrow" style={{ color: "#a9bbff" }}>A SMARTER WAY TO RUN RETAIL</div>
        <h2 className="login-art-title">Build your store<br />workspace.</h2>
        <p className="login-art-copy">One connected place for stock, sales, customers, and better decisions.</p>
      </div>
      <div className="login-art-foot">STOCKLY · RETAIL BILLING &amp; INVENTORY</div>
    </section>
    <section className="login-form-side register-side">
      <div className="login-card register-card">
        <Brand />
        <h1>Create your account</h1>
        <p className="intro">Join STOCKLY to get started with your store.</p>
        <form className="login-form register-form" onSubmit={handleSubmit(onSubmit)} noValidate>
          <div className="registration-grid">
            <FormInput id="firstName" label="First name" autoComplete="given-name" icon={<UserRound size={16} />} error={errors.firstName?.message} register={register("firstName")} />
            <FormInput id="lastName" label="Last name" autoComplete="family-name" icon={<UserRound size={16} />} error={errors.lastName?.message} register={register("lastName")} />
            <FormInput id="email" label="Email address" type="email" autoComplete="email" icon={<Mail size={16} />} error={errors.email?.message} register={register("email")} />
            <FormInput id="mobileNumber" label="Mobile number" type="tel" autoComplete="tel" icon={<Phone size={16} />} error={errors.mobileNumber?.message} register={register("mobileNumber")} />
            <FormInput id="username" label="Username" autoComplete="username" icon={<UserRound size={16} />} error={errors.username?.message} register={register("username")} />
            <div className="field register-password">
              <label htmlFor="register-password">Password</label>
              <div className="auth-input-wrap"><LockKeyhole size={16} aria-hidden="true" /><input id="register-password" className="input auth-input auth-password-input" type={showPassword ? "text" : "password"} autoComplete="new-password" aria-invalid={Boolean(errors.password)} {...register("password")} /><button className="password-toggle" type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? "Hide password" : "Show password"}>{showPassword ? <EyeOff size={16} /> : <Eye size={16} />}</button></div>
              {errors.password && <span className="form-error">{errors.password.message}</span>}
            </div>
            <FormInput id="confirmPassword" label="Confirm password" type={showPassword ? "text" : "password"} autoComplete="new-password" icon={<LockKeyhole size={16} />} error={errors.confirmPassword?.message} register={register("confirmPassword")} />
          </div>
          <label className="terms-row"><input type="checkbox" {...register("acceptTerms")} /><span>I accept the Terms and Conditions.</span></label>
          {errors.acceptTerms && <span className="form-error">{errors.acceptTerms.message}</span>}
          {submitError && <div className="notice error" role="alert">{submitError}</div>}
          <div className="notice warning register-role-note"><span>New public accounts receive the <strong>Billing Staff</strong> role and remain inactive until a store administrator activates them. Owner and administrator roles cannot be self-assigned.</span></div>
          <button className="btn btn-primary auth-submit" type="submit" disabled={isSubmitting}>{isSubmitting ? <><span className="spinner spinner-light" />Creating account…</> : <>Create Account <ArrowRight size={16} /></>}</button>
        </form>
        <p className="auth-switch">Already have an account? <Link href="/login">Login</Link></p>
      </div>
    </section>
  </main>;
}

function FormInput({ id, label, type = "text", autoComplete, icon, error, register }: {
  id: string;
  label: string;
  type?: string;
  autoComplete: string;
  icon: React.ReactNode;
  error?: string;
  register: ReturnType<ReturnType<typeof useForm<RegistrationValues>>["register"]>;
}) {
  return <div className="field">
    <label htmlFor={id}>{label}</label>
    <div className="auth-input-wrap">{icon}<input id={id} type={type} className="input auth-input" autoComplete={autoComplete} aria-invalid={Boolean(error)} {...register} /></div>
    {error && <span className="form-error">{error}</span>}
  </div>;
}

function Brand({ dark = false }: { dark?: boolean }) {
  return <div className={`brand auth-brand ${dark ? "auth-brand-dark" : ""}`}>
    <div className="brand-mark"><Boxes size={20} /></div>
    <div><div className="brand-name">STOCKLY</div><div className="brand-caption">SMARTER STOCK. FASTER SALES. BETTER DECISIONS.</div></div>
  </div>;
}
