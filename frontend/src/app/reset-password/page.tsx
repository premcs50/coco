"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeft, Boxes, KeyRound } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { api, apiError } from "@/lib/api";

const schema = z.object({
  token: z.string().min(1, "Enter your reset token"),
  newPassword: z.string().min(10).max(72).regex(/[A-Z]/).regex(/[a-z]/).regex(/\d/).regex(/[^A-Za-z0-9]/),
});
type Values = z.infer<typeof schema>;

export default function ResetPasswordPage() {
  const router = useRouter();
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Values>({ resolver: zodResolver(schema) });
  const submit = async (values: Values) => {
    try {
      await api.post("/auth/password-reset", values);
      setSuccess("Password updated. You can now sign in with your new password.");
      setError("");
      window.setTimeout(() => router.replace("/login"), 1800);
    } catch (requestError) { setError(apiError(requestError).message); }
  };
  return <main className="login-screen"><section className="login-form-side"><div className="login-card">
    <div className="brand" style={{ color: "var(--ink)", padding: "0 0 27px" }}><div className="brand-mark"><Boxes size={20} color="white" /></div><div><div className="brand-name" style={{ color: "var(--navy)" }}>STOCKLY</div><div className="brand-caption">SECURE ACCOUNT RECOVERY</div></div></div>
    <h1>Choose a new password</h1><p className="intro">Reset links are single use and expire after 30 minutes.</p>
    <form className="login-form" onSubmit={handleSubmit(submit)}>
      <div className="field"><label htmlFor="token">Reset token</label><input id="token" className="input" autoComplete="one-time-code" {...register("token")} />{errors.token && <span className="form-error">{errors.token.message}</span>}</div>
      <div className="field"><label htmlFor="new-password">New password</label><div style={{ position: "relative" }}><KeyRound size={15} style={{ color: "#95a0b4", left: 12, position: "absolute", top: 12 }} /><input id="new-password" className="input" style={{ paddingLeft: 36 }} type="password" autoComplete="new-password" {...register("newPassword")} /></div><small className="field-help">10–72 chars; uppercase, lowercase, number, and symbol.</small>{errors.newPassword && <span className="form-error">{errors.newPassword.message}</span>}</div>
      {error && <div className="notice error">{error}</div>}{success && <div className="notice">{success}</div>}
      <button className="btn btn-primary" type="submit" disabled={isSubmitting}>{isSubmitting ? "Updating…" : "Update password"}</button>
    </form>
    <Link href="/login" className="back-link"><ArrowLeft size={14} /> Back to sign in</Link>
  </div></section></main>;
}
