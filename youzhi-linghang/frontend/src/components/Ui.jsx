import { X } from "lucide-react";

export function Card({ className = "", children, ...props }) {
  return (
    <div
      className={`rounded-xl border border-slate-200 bg-white text-slate-950 shadow-sm transition-all duration-200 ${className}`}
      {...props}
    >
      {children}
    </div>
  );
}

export function Avatar({ src, fallback, className = "" }) {
  return (
    <div className={`relative flex h-10 w-10 shrink-0 overflow-hidden rounded-full ${className}`}>
      {src ? <img src={src} alt="" className="aspect-square h-full w-full object-cover" /> : null}
      <span
        className={`absolute inset-0 -z-10 flex h-full w-full items-center justify-center rounded-full bg-slate-100 text-slate-500 ${
          src ? "" : "avatar-fallback-visible"
        }`}
      >
        {fallback}
      </span>
    </div>
  );
}

export function Modal({ open, title, description, onClose, children }) {
  if (!open) return null;
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className="modal-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <button className="modal-close" onClick={onClose} aria-label="关闭">
          <X size={18} />
        </button>
        <h2 id="modal-title" className="text-xl font-bold text-slate-800 mb-2">
          {title}
        </h2>
        {description ? <p className="text-sm text-slate-500 mb-6">{description}</p> : null}
        {children}
      </section>
    </div>
  );
}

export function Toast({ message }) {
  if (!message) return null;
  return (
    <div className="toast" role="status">
      {message}
    </div>
  );
}

export function LoadingGrid({ rows = 3 }) {
  return (
    <>
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="skeleton rounded-xl" aria-hidden="true" />
      ))}
    </>
  );
}
