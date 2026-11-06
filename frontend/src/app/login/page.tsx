"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowRight, Boxes, Eye, EyeOff, LockKeyhole, Mail } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useRouter } from "next/navigation";
import { z } from "zod";
import { useAuth } from "@/context/AuthContext";
import { homePathForRole } from "@/lib/auth";

const schema = z.object({
  identifier: z.string().trim().min(1, "Enter your email address or username"),
  password: z.string().min(1, "Enter your password"),
});
type LoginValues = z.infer<typeof schema>;

export default function LoginPage() {
  const { user, loading, login, error } = useAuth();
  const router = useRouter();
  const [notice, setNotice] = useState({ expired: false, denied: false, registered: false });
  const [showPassword, setShowPassword] = useState(false);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginValues>({
    resolver: zodResolver(schema),
  });

  useEffect(() => {
    if (!loading && user) router.replace(homePathForRole(user.role));
  }, [loading, user, router]);
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    setNotice({
      expired: params.get("expired") === "1",
      denied: params.get("denied") === "1",
      registered: params.get("registered") === "1",
    });
  }, []);

  const onSubmit = async (values: LoginValues) => {
    try { await login(values.identifier, values.password); } catch { /* The auth context exposes the error. */ }
  };

  if (loading || user) return <div className="loading"><span className="spinner" />Loading STOCKLY…</div>;
  return (
    <main className="login-screen">
      <section className="login-art">
        <Brand dark />
        <div className="login-art-message">
          <div className="eyebrow" style={{ color: "#a9bbff" }}>SMARTER STOCK. FASTER SALES. BETTER DECISIONS.</div>
          <h2 className="login-art-title">Your whole store,<br />working in sync.</h2>
          <p className="login-art-copy">A clearer view of inventory, sales, customers, and the decisions that move your business forward.</p>
        </div>
        <div className="login-art-foot">STOCKLY · RETAIL BILLING &amp; INVENTORY</div>
      </section>
      <section className="login-form-side">
        <div className="login-card">
          <Brand />
          <h1>Welcome back</h1>
          <p className="intro">Sign in to your workspace to continue.</p>
          {notice.expired && <div className="notice warning" role="status">Your session expired. Please sign in again.</div>}
          {notice.denied && <div className="notice error" role="alert">You don’t have permission to access that page.</div>}
          {notice.registered && <div className="notice success" role="status"><strong>Your account has been created successfully!</strong><br />It is inactive until a store administrator activates it. You can sign in once it is active.</div>}
          <form className="login-form" onSubmit={handleSubmit(onSubmit)} noValidate>
            <div className="field">
              <label htmlFor="identifier">Email address or username</label>
              <div className="auth-input-wrap"><Mail size={16} aria-hidden="true" /><input id="identifier" className="input auth-input" autoComplete="username" placeholder="you@example.com or username" aria-invalid={Boolean(errors.identifier)} {...register("identifier")} /></div>
              {errors.identifier && <span className="form-error">{errors.identifier.message}</span>}
            </div>
            <div className="field">
              <label htmlFor="password">Password</label>
              <div className="auth-input-wrap"><LockKeyhole size={16} aria-hidden="true" /><input id="password" type={showPassword ? "text" : "password"} className="input auth-input auth-password-input" autoComplete="current-password" placeholder="Enter your password" aria-invalid={Boolean(errors.password)} {...register("password")} /><button className="password-toggle" type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? "Hide password" : "Show password"}>{showPassword ? <EyeOff size={16} /> : <Eye size={16} />}</button></div>
              {errors.password && <span className="form-error">{errors.password.message}</span>}
            </div>
            {error && <div className="notice error" role="alert">{error}</div>}
            <div className="auth-links"><span className="session-note">Your sign-in follows the store session policy.</span><Link href="/forgot-password">Forgot Password?</Link></div>
            <button className="btn btn-primary auth-submit" type="submit" disabled={isSubmitting}>{isSubmitting ? <><span className="spinner spinner-light" />Signing in…</> : <>Login <ArrowRight size={16} /></>}</button>
          </form>
          <p className="auth-switch">Don’t have an account? <Link href="/register">Sign Up</Link></p>
        </div>
      </section>
    </main>
  );
}

function Brand({ dark = false }: { dark?: boolean }) {
  return <div className={`brand auth-brand ${dark ? "auth-brand-dark" : ""}`}>
    <div className="brand-mark"><Boxes size={20} /></div>
    <div><div className="brand-name">STOCKLY</div><div className="brand-caption">SMARTER STOCK. FASTER SALES. BETTER DECISIONS.</div></div>
  </div>;
}
