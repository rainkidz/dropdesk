import { ArrowLeft, Compass } from 'lucide-react';
import { Link } from 'wouter';

export default function NotFound() {
  return (
    <div className="mx-auto flex min-h-[calc(100dvh-72px)] max-w-[860px] items-center px-5 py-12 sm:px-10">
      <div className="max-w-xl">
        <div className="mb-7 flex size-14 items-center justify-center rounded-2xl bg-[hsl(var(--accent)/.22)] text-[hsl(var(--accent-foreground))]">
          <Compass className="size-7" />
        </div>
        <p className="font-mono text-[10px] uppercase tracking-[0.18em] text-[hsl(var(--muted-foreground))]">404 · off the desk</p>
        <h1 data-testid="text-not-found-heading" className="mt-4 text-5xl font-extrabold tracking-[-0.07em] sm:text-7xl">That page wandered off.</h1>
        <p className="mt-5 max-w-md text-base leading-7 text-[hsl(var(--muted-foreground))]">There is nothing to download here. Head back to the desk and paste a public link instead.</p>
        <Link href="/" data-testid="link-not-found-home" className="mt-8 inline-flex items-center gap-2 rounded-xl bg-[hsl(var(--primary))] px-5 py-3 text-sm font-bold text-[hsl(var(--primary-foreground))] shadow-[3px_3px_0_hsl(var(--accent))]">
          <ArrowLeft className="size-4" /> Back to the desk
        </Link>
      </div>
    </div>
  );
}