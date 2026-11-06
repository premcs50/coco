"use client";

import Link from "next/link";
import { ArrowLeft, Boxes, ShieldAlert } from "lucide-react";

export default function ForgotPasswordPage() {
  return <main className="login-screen"><section className="login-form-side"><div className="login-card">
    <div className="brand" style={{ color: "var(--ink)", padding: "0 0 27px" }}><div className="brand-mark"><Boxes size={20} color="white" /></div><div><div className="brand-name" style={{ color: "var(--navy)" }}>STOCKLY</div><div className="brand-caption">ACCOUNT RECOVERY</div></div></div>
    <h1>Reset your password</h1><p className="intro">Password reset is handled by your store administrator.</p>
    <div className="notice warning"><ShieldAlert size={18} /><span>The backend does not provide a public password-reset request or email delivery endpoint. Contact an administrator; they can issue a single-use token and share it with you through a verified channel.</span></div>
    <Link href="/reset-password" className="btn btn-primary" style={{ marginTop: 18, width: "100%" }}>I have a reset token <ArrowLeft size={15} /></Link>
    <Link href="/login" className="back-link">Back to sign in</Link>
  </div></section></main>;
}
