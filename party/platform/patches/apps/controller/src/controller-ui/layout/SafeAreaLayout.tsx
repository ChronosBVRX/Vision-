import type { PropsWithChildren } from "react";

export function SafeAreaLayout({ children }: PropsWithChildren) {
  return (
    <>
      <div className="controller-ambient" aria-hidden="true" />
      <div className="controller-vignette" aria-hidden="true" />
      <div
      className="controller-content"
      style={{
        minHeight: "100dvh",
        boxSizing: "border-box",
        padding: "max(10px, env(safe-area-inset-top)) max(8px, env(safe-area-inset-right)) max(10px, env(safe-area-inset-bottom)) max(8px, env(safe-area-inset-left))"
      }}
    >
      {children}
      </div>
    </>
  );
}
