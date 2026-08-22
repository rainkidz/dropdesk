import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { QueryClient, QueryClientProvider, useQueryClient } from '@tanstack/react-query';
import { Link, Route, Router as WouterRouter, Switch, useLocation } from 'wouter';
import {
  AlertTriangle,
  ArrowDownToLine,
  ArrowRight,
  Check,
  CheckCircle2,
  CircleHelp,
  Clock3,
  Crown,
  Download,
  FileAudio,
  FileVideo,
  Instagram,
  Link2,
  LoaderCircle,
  LockKeyhole,
  Menu,
  MessageCircle,
  Music2,
  Radio,
  RotateCcw,
  ScanLine,
  Settings,
  ShieldCheck,
  Sparkles,
  Youtube,
  type LucideIcon,
} from 'lucide-react';

function FacebookIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor"><path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z"/></svg>
  );
}
import {
  getDownloadFileQueryKey,
  getGetDownloadQueryKey,
  getListRecentDownloadsQueryKey,
  useCreateDownload,
  useDownloadFile,
  useGetDownload,
  useHealthCheck,
  useInspectDownload,
  useListRecentDownloads,
  type DownloadFormat,
  type DownloadInspection,
  type DownloadJob,
} from '@workspace/api-client-react';
import { ErrorBoundary } from '@/components/error-boundary';
import { Toaster } from '@/components/ui/toaster';
import { TooltipProvider } from '@/components/ui/tooltip';
import NotFound from '@/pages/not-found';

const queryClient = new QueryClient();

const platformMeta = {
  youtube: { label: 'YouTube', Icon: Youtube, tone: 'text-red-600 bg-red-50' },
  instagram: { label: 'Instagram', Icon: Instagram, tone: 'text-pink-700 bg-pink-50' },
  facebook: { label: 'Facebook', Icon: FacebookIcon as unknown as LucideIcon, tone: 'text-blue-700 bg-blue-50' },
  threads: { label: 'Threads', Icon: MessageCircle, tone: 'text-slate-700 bg-slate-100' },
  tiktok: { label: 'TikTok', Icon: Music2, tone: 'text-cyan-800 bg-cyan-50' },
  unknown: { label: 'Unknown source', Icon: Link2, tone: 'text-slate-600 bg-slate-100' },
} as const;

function platformDetails(platform: DownloadInspection['platform']) {
  return platformMeta[platform] ?? platformMeta.unknown;
}

function formatDuration(seconds: number | null | undefined) {
  if (!seconds) return 'Length unavailable';
  const minutes = Math.floor(seconds / 60);
  const remainder = Math.round(seconds % 60).toString().padStart(2, '0');
  return `${minutes}:${remainder}`;
}

function formatRelativeDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Just now';
  const diff = Math.max(0, Date.now() - date.getTime());
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return 'Just now';
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function shortenUrl(url: string) {
  try {
    const parsed = new URL(url);
    return `${parsed.hostname.replace(/^www\./, '')}${parsed.pathname.length > 22 ? `${parsed.pathname.slice(0, 22)}…` : parsed.pathname}`;
  } catch {
    return url.length > 38 ? `${url.slice(0, 38)}…` : url;
  }
}

function apiErrorMessage(error: unknown, fallback: string) {
  if (error && typeof error === 'object') {
    const data = (error as { data?: unknown }).data;
    if (data && typeof data === 'object' && typeof (data as { error?: unknown }).error === 'string') {
      return (data as { error: string }).error;
    }
    const message = (error as { message?: unknown }).message;
    if (typeof message === 'string' && message.length > 0) return message;
  }
  return fallback;
}

function StatusPill({ status }: { status: DownloadJob['status'] }) {
  const config = {
    queued: { label: 'Queued', icon: Clock3, className: 'bg-amber-100 text-amber-800' },
    downloading: { label: 'Downloading', icon: LoaderCircle, className: 'bg-sky-100 text-sky-800' },
    completed: { label: 'Ready', icon: CheckCircle2, className: 'bg-emerald-100 text-emerald-800' },
    failed: { label: 'Could not fetch', icon: AlertTriangle, className: 'bg-red-100 text-red-800' },
  }[status];
  const Icon = config.icon;
  return (
    <span data-testid={`status-job-${status}`} className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-bold uppercase tracking-[0.08em] ${config.className}`}>
      <Icon className={status === 'downloading' ? 'size-3 animate-spin' : 'size-3'} />
      {config.label}
    </span>
  );
}

function BrandMark() {
  return (
    <div className="flex items-center gap-3">
      <div className="relative flex size-10 items-center justify-center rounded-xl bg-[hsl(var(--accent))] text-[hsl(var(--primary))] shadow-[4px_4px_0_hsl(var(--primary))]">
        <ArrowDownToLine className="size-5 stroke-[2.5]" />
        <span className="absolute -right-1 -top-1 size-2 rounded-full border-2 border-[hsl(var(--sidebar))] bg-[hsl(var(--accent))]" />
      </div>
      <div>
        <div className="font-extrabold tracking-[-0.04em] text-[hsl(var(--sidebar-foreground))]">dropdesk</div>
        <div className="font-mono text-[9px] uppercase tracking-[0.18em] text-[hsl(var(--sidebar-foreground)/.52)]">public media desk</div>
      </div>
    </div>
  );
}

function Shell({ children }: { children: ReactNode }) {
  const [location] = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [showSettings, setShowSettings] = useState(() => {
    if (isNativeApp()) {
      return !localStorage.getItem('dropdesk_server_url');
    }
    return false;
  });
  const health = useHealthCheck();
  const navItems = [
    { href: '/', label: 'Download desk', icon: ScanLine },
    { href: '/privacy', label: 'Privacy & use', icon: ShieldCheck },
  ];

  return (
    <div className="grain min-h-[100dvh] bg-[hsl(var(--background))] text-[hsl(var(--foreground))]">
      {showSettings && <ServerSettings />}
      <aside className={`fixed inset-y-0 left-0 z-30 flex w-[258px] flex-col bg-[hsl(var(--sidebar))] px-5 py-6 text-[hsl(var(--sidebar-foreground))] transition-transform duration-300 md:translate-x-0 ${mobileOpen ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="mb-12 flex items-center justify-between">
          <BrandMark />
          <button data-testid="button-close-mobile-navigation" className="rounded-md p-2 text-white/50 hover:bg-white/10 md:hidden" onClick={() => setMobileOpen(false)} aria-label="Close navigation">
            <Menu className="size-5" />
          </button>
        </div>
        <div className="mb-4 px-3 font-mono text-[10px] uppercase tracking-[0.16em] text-white/35">Workspace</div>
        <nav className="space-y-1" aria-label="Primary navigation">
          {navItems.map(({ href, label, icon: Icon }) => (
            <Link
              key={href}
              href={href}
              data-testid={`link-${label.toLowerCase().replaceAll(' ', '-')}`}
              onClick={() => setMobileOpen(false)}
              className={`group flex items-center gap-3 rounded-lg px-3 py-3 text-sm font-semibold transition-colors ${location === href ? 'bg-white/10 text-[hsl(var(--accent))]' : 'text-white/60 hover:bg-white/5 hover:text-white'}`}
            >
              <Icon className="size-[17px]" />
              {label}
              {location === href && <span className="ml-auto size-1.5 rounded-full bg-[hsl(var(--accent))]" />}
            </Link>
          ))}
        </nav>
        {isNativeApp() && (
          <button onClick={() => setShowSettings(true)} className="mb-3 flex items-center gap-3 rounded-lg px-3 py-2.5 text-xs font-semibold text-white/50 hover:bg-white/5 hover:text-white">
            <Settings className="size-4" />
            Server settings
          </button>
        )}
        <div className="mt-auto rounded-xl border border-white/10 bg-white/[0.04] p-4">
          <div className="mb-3 flex items-center gap-2 text-xs font-bold text-white/80">
            <span className={`size-2 rounded-full ${health.isError ? 'bg-red-400' : 'bg-emerald-400'}`} />
            Desk status
          </div>
          <p data-testid="status-health" className="text-[11px] leading-relaxed text-white/45">
            {health.isLoading ? 'Checking the download desk…' : health.isError ? 'The service is taking a breather. Try again in a moment.' : 'Ready to inspect public links.'}
          </p>
        </div>
      </aside>
      {mobileOpen && <button data-testid="button-close-navigation-overlay" className="fixed inset-0 z-20 bg-[hsl(var(--primary)/.45)] md:hidden" onClick={() => setMobileOpen(false)} aria-label="Close navigation overlay" />}
      <div className="md:pl-[258px]">
        <header className="sticky top-0 z-10 flex h-[72px] items-center justify-between border-b border-[hsl(var(--border)/.75)] bg-[hsl(var(--background)/.88)] px-5 backdrop-blur-md sm:px-8">
          <button data-testid="button-open-navigation" className="rounded-lg border border-[hsl(var(--border))] bg-[hsl(var(--card))] p-2.5 md:hidden" onClick={() => setMobileOpen(true)} aria-label="Open navigation">
            <Menu className="size-5" />
          </button>
          <div className="hidden items-center gap-2 font-mono text-[10px] uppercase tracking-[0.15em] text-[hsl(var(--muted-foreground))] sm:flex">
            <Radio className="size-3.5 text-[hsl(var(--accent-foreground))]" />
            Your quiet corner of the internet
          </div>
          <Link href="/privacy" data-testid="link-header-privacy" className="ml-auto inline-flex items-center gap-2 text-xs font-bold text-[hsl(var(--muted-foreground))] transition-colors hover:text-[hsl(var(--foreground))]">
            <LockKeyhole className="size-3.5" />
            Privacy first
          </Link>
        </header>
        <main>{children}</main>
      </div>
    </div>
  );
}

function RecentJobs({ jobs, isLoading, isError, onRetry, onDownload }: {
  jobs: DownloadJob[];
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
  onDownload: (job: DownloadJob) => void;
}) {
  return (
    <section className="rise-in delay-3 mt-14" aria-labelledby="recent-heading">
      <div className="mb-5 flex items-end justify-between gap-4">
        <div>
          <div className="mb-2 font-mono text-[10px] font-medium uppercase tracking-[0.18em] text-[hsl(var(--muted-foreground))]">Your queue</div>
          <h2 id="recent-heading" className="text-2xl font-extrabold tracking-[-0.04em]">Recent saves</h2>
        </div>
        <span className="rounded-full bg-[hsl(var(--secondary))] px-3 py-1 font-mono text-[10px] uppercase tracking-[0.12em] text-[hsl(var(--muted-foreground))]">{jobs.length} items</span>
      </div>
      <div className="overflow-hidden rounded-2xl border border-[hsl(var(--border))] bg-[hsl(var(--card)/.8)]">
        {isLoading ? (
          <div className="space-y-3 p-5" data-testid="loading-recent-jobs">
            {[1, 2, 3].map((item) => <div key={item} className="shimmer h-16 rounded-xl" />)}
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center justify-center px-6 py-12 text-center" data-testid="error-recent-jobs">
            <AlertTriangle className="mb-3 size-7 text-[hsl(var(--destructive))]" />
            <p className="font-bold">Recent saves are unavailable</p>
            <p className="mt-1 max-w-xs text-sm text-[hsl(var(--muted-foreground))]">Your next download can still start. We just could not load the queue.</p>
            <button data-testid="button-retry-recent-jobs" onClick={onRetry} className="mt-4 inline-flex items-center gap-2 rounded-lg border border-[hsl(var(--border))] px-3 py-2 text-xs font-bold hover:bg-[hsl(var(--muted))]"><RotateCcw className="size-3.5" /> Try again</button>
          </div>
        ) : jobs.length === 0 ? (
          <div className="flex flex-col items-center justify-center px-6 py-14 text-center" data-testid="empty-recent-jobs">
            <div className="mb-4 flex size-12 items-center justify-center rounded-2xl bg-[hsl(var(--secondary))] text-[hsl(var(--muted-foreground))]"><ArrowDownToLine className="size-5" /></div>
            <p className="font-bold">Your desk is clear</p>
            <p className="mt-1 max-w-xs text-sm text-[hsl(var(--muted-foreground))]">Inspect a public link above and your saved references will land here.</p>
          </div>
        ) : (
          <div className="divide-y divide-[hsl(var(--border)/.75)]">
            {jobs.map((job) => {
              const meta = platformDetails(job.platform);
              const Icon = meta.Icon;
              return (
                <div key={job.id} data-testid={`row-recent-job-${job.id}`} className="group flex flex-col gap-3 px-5 py-4 transition-colors hover:bg-[hsl(var(--muted)/.45)] sm:flex-row sm:items-center">
                  <div className={`flex size-9 shrink-0 items-center justify-center rounded-lg ${meta.tone}`}><Icon className="size-4" /></div>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p data-testid={`text-job-title-${job.id}`} className="max-w-[360px] truncate text-sm font-bold">{job.title || shortenUrl(job.url)}</p>
                      <StatusPill status={job.status} />
                    </div>
                    <p data-testid={`text-job-meta-${job.id}`} className="mt-1 font-mono text-[10px] uppercase tracking-[0.08em] text-[hsl(var(--muted-foreground))]">{meta.label} · {job.mediaType} · {formatRelativeDate(job.createdAt)}</p>
                    {job.status === 'downloading' && <div className="mt-2 h-1 max-w-[260px] overflow-hidden rounded-full bg-[hsl(var(--secondary))]"><div className="h-full rounded-full bg-[hsl(var(--accent))] transition-all" style={{ width: `${job.progress}%` }} /></div>}
                    {job.status === 'failed' && job.error && <p className="mt-1 text-xs text-[hsl(var(--destructive))]">{job.error}</p>}
                  </div>
                  {job.status === 'completed' && (
                    <button data-testid={`button-download-job-${job.id}`} onClick={() => onDownload(job)} className="inline-flex items-center justify-center gap-2 rounded-lg border border-[hsl(var(--border))] bg-[hsl(var(--card))] px-3 py-2 text-xs font-bold transition-colors hover:border-[hsl(var(--accent))] hover:bg-[hsl(var(--accent)/.15)]">
                      <Download className="size-3.5" /> Save file
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </section>
  );
}

function InspectionCard({ inspection, selectedFormat, setSelectedFormat, onDownload, isPending, error }: {
  inspection: DownloadInspection;
  selectedFormat: DownloadFormat | undefined;
  setSelectedFormat: (format: DownloadFormat) => void;
  onDownload: () => void;
  isPending: boolean;
  error: unknown;
}) {
  const meta = platformDetails(inspection.platform);
  const Icon = meta.Icon;
  const premiumFormats = inspection.formats.filter((format) => format.kind === 'premium');
  const videoFormats = inspection.formats.filter((format) => format.kind === 'video');
  const audioFormats = inspection.formats.filter((format) => format.kind === 'audio');
  return (
    <div className="rise-in mt-6 overflow-hidden rounded-2xl border border-[hsl(var(--border))] bg-[hsl(var(--card))] shadow-[0_18px_50px_hsl(var(--primary)/.08)]" data-testid="card-inspection-result">
      <div className="grid md:grid-cols-[minmax(180px,0.8fr)_1.2fr]">
        <div className="relative min-h-[200px] overflow-hidden bg-[hsl(var(--primary))]">
          {inspection.thumbnailUrl ? <img data-testid="img-inspection-thumbnail" src={inspection.thumbnailUrl} alt="" className="absolute inset-0 size-full object-cover opacity-60" /> : <div className="absolute inset-0 desk-grid opacity-20" />}
          <div className="absolute inset-0 bg-gradient-to-t from-[hsl(var(--primary))] via-[hsl(var(--primary)/.2)] to-transparent" />
          <div className="relative flex h-full min-h-[200px] flex-col justify-between p-5 text-white">
            <span className={`flex size-10 items-center justify-center rounded-xl ${meta.tone}`}><Icon className="size-5" /></span>
            <div>
              <div className="mb-2 font-mono text-[10px] uppercase tracking-[0.17em] text-white/55">{meta.label} · {formatDuration(inspection.durationSeconds)}</div>
              <h3 data-testid="text-inspection-title" className="line-clamp-3 text-lg font-extrabold leading-tight">{inspection.title || 'Untitled public media'}</h3>
            </div>
          </div>
        </div>
        <div className="p-5 sm:p-6">
          <div className="mb-5 flex items-center justify-between">
            <div>
              <div className="font-mono text-[10px] uppercase tracking-[0.16em] text-[hsl(var(--muted-foreground))]">Choose a file</div>
              <p className="mt-1 text-sm text-[hsl(var(--muted-foreground))]">Pick the version that fits the reference.</p>
            </div>
            <CheckCircle2 className="size-5 text-[hsl(var(--chart-2))]" />
          </div>
          {!inspection.isSupported ? (
            <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800" data-testid="status-unsupported-link">
              <div className="mb-1 flex items-center gap-2 font-bold"><AlertTriangle className="size-4" /> This source is not supported</div>
              Public URLs from YouTube, Instagram, Threads, and TikTok work best here.
            </div>
          ) : (
            <>
              <div className="space-y-2">
                {premiumFormats.length > 0 && (
                  <>
                    <div className="mb-2 flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.14em] text-amber-600"><Crown className="size-3.5" /> Premium · Video + Audio</div>
                    {premiumFormats.map((format) => <FormatChoice key={format.id} format={format} selected={selectedFormat?.id === format.id} onSelect={() => setSelectedFormat(format)} premium />)}
                  </>
                )}
                {videoFormats.length > 0 && (
                  <>
                    <div className="mb-2 mt-5 flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.14em] text-[hsl(var(--muted-foreground))]"><FileVideo className="size-3.5" /> Video only</div>
                    {videoFormats.map((format) => <FormatChoice key={format.id} format={format} selected={selectedFormat?.id === format.id} onSelect={() => setSelectedFormat(format)} />)}
                  </>
                )}
                {audioFormats.length > 0 && (
                  <>
                    <div className="mb-2 mt-5 flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.14em] text-[hsl(var(--muted-foreground))]"><FileAudio className="size-3.5" /> Audio only</div>
                    {audioFormats.map((format) => <FormatChoice key={format.id} format={format} selected={selectedFormat?.id === format.id} onSelect={() => setSelectedFormat(format)} />)}
                  </>
                )}
              </div>
              {error && <p data-testid="error-create-download" className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-xs font-semibold text-red-700">{apiErrorMessage(error, 'Could not start this download.')}</p>}
              <button data-testid="button-start-download" disabled={!selectedFormat || isPending} onClick={onDownload} className="mt-5 flex w-full items-center justify-center gap-2 rounded-xl bg-[hsl(var(--primary))] px-4 py-3.5 text-sm font-bold text-[hsl(var(--primary-foreground))] shadow-[4px_4px_0_hsl(var(--accent))] transition-transform hover:-translate-y-0.5 disabled:cursor-wait disabled:opacity-60">
                {isPending ? <LoaderCircle className="size-4 animate-spin" /> : <ArrowDownToLine className="size-4" />}
                {isPending ? 'Starting your save…' : 'Start download'}
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function FormatChoice({ format, selected, onSelect, premium }: { format: DownloadFormat; selected: boolean; onSelect: () => void; premium?: boolean }) {
  return (
    <button type="button" data-testid={`button-format-${format.id}`} onClick={onSelect} className={`flex w-full items-center gap-3 rounded-xl border px-3.5 py-3 text-left transition-colors ${selected ? (premium ? 'border-amber-400 bg-amber-50' : 'border-[hsl(var(--accent))] bg-[hsl(var(--accent)/.14)]') : 'border-[hsl(var(--border))] hover:bg-[hsl(var(--muted)/.7)]'}`}>
      <span className={`flex size-7 items-center justify-center rounded-full border ${selected ? (premium ? 'border-amber-400 bg-amber-400 text-white' : 'border-[hsl(var(--accent))] bg-[hsl(var(--accent))] text-[hsl(var(--primary))]') : 'border-[hsl(var(--border))] text-transparent'}`}><Check className="size-3.5" /></span>
      <span className="min-w-0 flex-1"><span className="block text-sm font-bold">{format.label}</span><span className="font-mono text-[10px] uppercase tracking-[0.08em] text-[hsl(var(--muted-foreground))]">.{format.extension}</span></span>
      {format.sizeLabel && <span className="font-mono text-[10px] text-[hsl(var(--muted-foreground))]">{format.sizeLabel}</span>}
    </button>
  );
}

function InstagramCookiesSetup() {
  const [cookies, setCookies] = useState('');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [showHelp, setShowHelp] = useState(false);

  const handleSave = async () => {
    if (!cookies.trim()) return;
    setSaving(true);
    try {
      const res = await fetch('/api/instagram/cookies', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ cookies: cookies.trim() }),
      });
      if (res.ok) setSaved(true);
    } catch {}
    setSaving(false);
  };

  return (
    <div className="mt-4 rounded-xl border border-pink-200 bg-pink-50 p-4">
      <div className="mb-2 flex items-center gap-2">
        <Instagram className="size-4 text-pink-600" />
        <span className="text-xs font-bold text-pink-800">Instagram Cookies Required</span>
      </div>
      <p className="mb-3 text-[11px] text-pink-700/80">Instagram membutuhkan login untuk download. Masukkan cookies dari browser Anda.</p>
      {saved ? (
        <div className="flex items-center gap-2 text-xs font-semibold text-emerald-700"><CheckCircle2 className="size-4" /> Cookies tersimpan! Coba lagi inspect link Instagram.</div>
      ) : (
      <>
        <button onClick={() => setShowHelp(!showHelp)} className="mb-2 text-[10px] font-bold text-pink-600 underline">{showHelp ? 'Sembunyikan' : 'Cara ambil cookies?'}</button>
        {showHelp && (
          <ol className="mb-3 list-decimal pl-4 text-[11px] text-pink-700/80 space-y-1">
            <li>Login Instagram di browser Chrome/Edge</li>
            <li>Buka DevTools (F12) → Application → Cookies → instagram.com</li>
            <li>Cari cookie <code className="bg-pink-100 px-1 rounded">session_id</code> dan <code className="bg-pink-100 px-1 rounded">ds_user_id</code></li>
            <li>Atau: install ekstensi <a href="https://chrome.google.com/webstore/detail/get-cookiestxt-locally/cclelndahbckbenkjhflpdbgdldlbecc" target="_blank" className="underline">Get cookies.txt</a>, klik ikonnya di halaman Instagram, copy semua isi</li>
          </ol>
        )}
        <textarea value={cookies} onChange={(e) => setCookies(e.target.value)} placeholder="# Netscape HTTP Cookie File
.instagram.com	TRUE	/	TRUE	...	sessionid	..." className="w-full rounded-lg border border-pink-200 bg-white p-2 font-mono text-[10px] text-pink-900 placeholder:text-pink-300 focus:outline-none focus:ring-2 focus:ring-pink-300" rows={4} />
        <button onClick={handleSave} disabled={!cookies.trim() || saving} className="mt-2 inline-flex items-center gap-1.5 rounded-lg bg-pink-600 px-3 py-1.5 text-[11px] font-bold text-white hover:bg-pink-700 disabled:opacity-50">
          {saving ? <LoaderCircle className="size-3 animate-spin" /> : null}
          {saving ? 'Menyimpan…' : 'Simpan Cookies'}
        </button>
      </>
      )}
    </div>
  );
}

function ServerSettings() {
  const [serverUrl, setServerUrl] = useState(() => localStorage.getItem('dropdesk_server_url') || '');
  const [saved, setSaved] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testError, setTestError] = useState('');

  const handleSave = async () => {
    if (!serverUrl.trim()) return;
    setTesting(true);
    setTestError('');
    try {
      const res = await fetch(`${serverUrl.replace(/\/+$/, '')}/api/healthz`, { signal: AbortSignal.timeout(5000) });
      const data = await res.json();
      if (data.status === 'ok') {
        localStorage.setItem('dropdesk_server_url', serverUrl.trim());
        window.location.reload();
      } else {
        setTestError('Server responded but is not healthy');
      }
    } catch (err) {
      setTestError('Cannot connect to server. Check the URL and make sure the server is running.');
    }
    setTesting(false);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[hsl(var(--background))] p-5">
      <div className="w-full max-w-md rounded-2xl border border-[hsl(var(--border))] bg-[hsl(var(--card))] p-6 shadow-2xl">
        <div className="mb-6 flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-[hsl(var(--accent))]">
            <Radio className="size-5 text-[hsl(var(--primary))]" />
          </div>
          <div>
            <h2 className="text-lg font-extrabold">Connect to Server</h2>
            <p className="text-xs text-[hsl(var(--muted-foreground))]">Enter the address of your Dropdesk server</p>
          </div>
        </div>
        <div className="space-y-3">
          <div>
            <label className="mb-1 block font-mono text-[10px] uppercase tracking-[0.14em] text-[hsl(var(--muted-foreground))]">Server URL</label>
            <input
              type="url"
              value={serverUrl}
              onChange={(e) => setServerUrl(e.target.value)}
              placeholder="http://192.168.1.100:5000"
              className="w-full rounded-xl border border-[hsl(var(--border))] bg-[hsl(var(--background))] px-4 py-3 text-sm font-semibold outline-none focus:ring-2 focus:ring-[hsl(var(--accent))]"
            />
          </div>
          <p className="text-[11px] text-[hsl(var(--muted-foreground))]">
            Start the server on your computer:
          </p>
          <pre className="rounded-lg bg-[hsl(var(--primary))] p-3 font-mono text-[10px] text-[hsl(var(--primary-foreground))]">
{`PORT=5000 node --enable-source-maps artifacts/api-server/dist/index.mjs`}
          </pre>
          {testError && <div className="rounded-lg bg-red-50 px-3 py-2 text-xs font-semibold text-red-700">{testError}</div>}
          <button onClick={handleSave} disabled={!serverUrl.trim() || testing} className="flex w-full items-center justify-center gap-2 rounded-xl bg-[hsl(var(--primary))] px-4 py-3 text-sm font-bold text-[hsl(var(--primary-foreground))] shadow-[3px_3px_0_hsl(var(--accent))] hover:-translate-y-0.5 disabled:opacity-50">
            {testing ? <LoaderCircle className="size-4 animate-spin" /> : <ArrowRight className="size-4" />}
            {testing ? 'Connecting…' : 'Connect & Start'}
          </button>
        </div>
      </div>
    </div>
  );
}

function isNativeApp() {
  return window.location.protocol === 'capacitor:' ||
    (window.location.hostname === 'localhost' && window.location.pathname.includes('index.html'));
}

function Home() {
  const queryClient = useQueryClient();
  const [url, setUrl] = useState('');
  const [inspection, setInspection] = useState<DownloadInspection | undefined>();
  const [selectedFormat, setSelectedFormat] = useState<DownloadFormat>();
  const [activeJobId, setActiveJobId] = useState('');
  const [activeFileId, setActiveFileId] = useState('');
  const inspect = useInspectDownload();
  const create = useCreateDownload();
  const recent = useListRecentDownloads();
  const activeJob = useGetDownload(activeJobId, { query: { enabled: Boolean(activeJobId), queryKey: getGetDownloadQueryKey(activeJobId), refetchInterval: 1800 } });
  const file = useDownloadFile(activeFileId, { query: { enabled: Boolean(activeFileId), queryKey: getDownloadFileQueryKey(activeFileId) } });
  const jobs = useMemo(() => {
    const source = recent.data ?? [];
    if (!activeJob.data) return source;
    return source.some((job) => job.id === activeJob.data?.id) ? source.map((job) => job.id === activeJob.data?.id ? activeJob.data : job) : [activeJob.data, ...source];
  }, [recent.data, activeJob.data]);

  useEffect(() => {
    if (inspect.data) {
      setInspection(inspect.data);
      setSelectedFormat(inspect.data.formats[0]);
    }
  }, [inspect.data]);

  useEffect(() => {
    if (file.data instanceof Blob) {
      const objectUrl = URL.createObjectURL(file.data);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = activeJob.data?.filename || 'dropdesk-download';
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(objectUrl);
      setActiveFileId('');
    }
  }, [file.data, activeJob.data?.filename]);

  const handleInspect = (event: FormEvent) => {
    event.preventDefault();
    if (url.trim().length < 8) return;
    setInspection(undefined);
    inspect.mutate({ data: { url: url.trim() } });
  };

  const handleCreate = () => {
    if (!inspection || !selectedFormat) return;
    create.mutate({ data: { url: inspection.url, formatId: selectedFormat.id, mediaType: selectedFormat.kind, title: inspection.title } }, {
      onSuccess: (job) => {
        setActiveJobId(job.id);
        queryClient.invalidateQueries({ queryKey: getListRecentDownloadsQueryKey() });
      },
    });
  };

  const handleDownload = (job: DownloadJob) => {
    setActiveJobId(job.id);
    setActiveFileId(job.id);
  };

  return (
    <div className="mx-auto max-w-[1180px] px-5 py-10 sm:px-8 sm:py-14 lg:px-12">
      <section className="relative overflow-hidden rounded-[28px] bg-[hsl(var(--primary))] px-6 py-9 text-[hsl(var(--primary-foreground))] shadow-[0_24px_80px_hsl(var(--primary)/.18)] sm:px-10 sm:py-12">
        <div className="absolute -right-16 -top-20 size-64 rounded-full border-[34px] border-[hsl(var(--accent)/.22)]" />
        <div className="absolute bottom-[-110px] right-[22%] size-56 rounded-full border-[1px] border-white/10" />
        <div className="relative max-w-2xl">
          <div className="rise-in mb-7 inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/[0.07] px-3 py-1.5 font-mono text-[10px] uppercase tracking-[0.16em] text-white/70">
            <Sparkles className="size-3.5 text-[hsl(var(--accent))]" /> A better place to keep the good bits
          </div>
          <h1 className="rise-in delay-1 max-w-[680px] text-4xl font-extrabold leading-[.98] tracking-[-0.07em] sm:text-6xl">Save what matters.<br /><span className="text-[hsl(var(--accent))]">Leave the noise.</span></h1>
          <p className="rise-in delay-2 mt-6 max-w-[510px] text-sm leading-7 text-white/65 sm:text-base">Drop in a public link from the places you scroll. We inspect it first, then give you a clean file to keep for your next reference, edit, or offline moment.</p>
        </div>
        <div className="relative mt-9 max-w-[720px]">
          <form onSubmit={handleInspect} className="rise-in delay-3 flex flex-col gap-2 rounded-2xl bg-[hsl(var(--card))] p-2 shadow-[0_12px_30px_hsl(var(--primary)/.2)] sm:flex-row">
            <div className="flex min-w-0 flex-1 items-center gap-3 px-3">
              <Link2 className="size-5 shrink-0 text-[hsl(var(--muted-foreground))]" />
              <label htmlFor="social-url" className="sr-only">Public social URL</label>
              <input id="social-url" data-testid="input-social-url" value={url} onChange={(event) => setUrl(event.target.value)} placeholder="Paste a public link to get started" className="h-12 min-w-0 flex-1 bg-transparent text-sm font-semibold text-[hsl(var(--foreground))] outline-none placeholder:text-[hsl(var(--muted-foreground))]" />
              {url && <button type="button" data-testid="button-clear-url" onClick={() => setUrl('')} className="rounded-md p-1 text-[hsl(var(--muted-foreground))] hover:text-[hsl(var(--foreground))]" aria-label="Clear URL"><span className="text-lg leading-none">×</span></button>}
            </div>
            <button data-testid="button-inspect-url" type="submit" disabled={inspect.isPending || url.trim().length < 8} className="inline-flex h-12 items-center justify-center gap-2 rounded-xl bg-[hsl(var(--accent))] px-6 text-sm font-extrabold text-[hsl(var(--primary))] transition-transform hover:-translate-y-0.5 disabled:cursor-wait disabled:opacity-60">
              {inspect.isPending ? <LoaderCircle className="size-4 animate-spin" /> : <ScanLine className="size-4" />}
              {inspect.isPending ? 'Inspecting…' : 'Inspect link'}
            </button>
          </form>
          <div className="rise-in delay-4 mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 px-2 font-mono text-[10px] uppercase tracking-[0.12em] text-white/40">
            <span className="inline-flex items-center gap-1.5"><ShieldCheck className="size-3.5 text-emerald-300" /> Public links only</span>
            <span className="hidden size-1 rounded-full bg-white/25 sm:block" />
            <span>YouTube · Instagram · Facebook · Threads · TikTok</span>
          </div>
          {inspect.isError && <div data-testid="error-inspect-url" className="mt-4 flex items-start gap-2 rounded-xl border border-red-300/30 bg-red-400/10 px-4 py-3 text-xs font-semibold text-red-100"><AlertTriangle className="mt-0.5 size-4 shrink-0" />{apiErrorMessage(inspect.error, 'We could not inspect that link. Check the URL and try again.')}</div>}
          {inspect.isError && apiErrorMessage(inspect.error, '').includes('cookies') && <InstagramCookiesSetup />}
        </div>
      </section>

      {inspection && <InspectionCard inspection={inspection} selectedFormat={selectedFormat} setSelectedFormat={setSelectedFormat} onDownload={handleCreate} isPending={create.isPending} error={create.error} />}

      {!inspection && !inspect.isPending && (
        <section className="rise-in delay-2 mt-10 grid gap-5 sm:grid-cols-3">
          {[
            { number: '01', title: 'Paste once', body: 'No accounts, redirects, or puzzle boxes. Start with the public link you already have.' },
            { number: '02', title: 'See the file', body: 'We show the source, title, duration, and formats before anything begins.' },
            { number: '03', title: 'Keep it close', body: 'Download a clean copy for your own references and offline workflow.' },
          ].map((item) => <div key={item.number} className="border-t border-[hsl(var(--border))] pt-4"><span className="font-mono text-[10px] text-[hsl(var(--accent-foreground))]">{item.number}</span><h3 className="mt-4 text-sm font-extrabold">{item.title}</h3><p className="mt-2 text-sm leading-6 text-[hsl(var(--muted-foreground))]">{item.body}</p></div>)}
        </section>
      )}

      {inspect.isPending && <div className="mt-6 space-y-3" data-testid="loading-inspection"><div className="shimmer h-24 rounded-2xl" /><div className="shimmer h-40 rounded-2xl" /></div>}
      {activeJob.data && activeJob.data.status === 'downloading' && <div data-testid="status-active-download" className="mt-6 flex items-center gap-3 rounded-xl border border-[hsl(var(--accent)/.45)] bg-[hsl(var(--accent)/.12)] px-4 py-3 text-sm"><LoaderCircle className="size-4 animate-spin" /><span className="font-semibold">Your file is being prepared.</span><span className="ml-auto font-mono text-xs">{activeJob.data.progress}%</span></div>}
      <RecentJobs jobs={jobs} isLoading={recent.isLoading} isError={recent.isError} onRetry={() => recent.refetch()} onDownload={handleDownload} />

      <footer className="mt-16 flex flex-col justify-between gap-3 border-t border-[hsl(var(--border))] pt-5 text-[11px] text-[hsl(var(--muted-foreground))] sm:flex-row">
        <span>Dropdesk is for public media you have permission to save.</span>
        <Link href="/privacy" data-testid="link-footer-privacy" className="inline-flex items-center gap-1 font-bold hover:text-[hsl(var(--foreground))]">Read the privacy note <ArrowRight className="size-3" /></Link>
      </footer>
    </div>
  );
}

function Privacy() {
  return (
    <div className="mx-auto max-w-[980px] px-5 py-12 sm:px-8 sm:py-16 lg:px-12">
      <div className="rise-in max-w-2xl">
        <div className="mb-5 inline-flex items-center gap-2 rounded-full bg-[hsl(var(--accent)/.18)] px-3 py-1.5 font-mono text-[10px] uppercase tracking-[0.16em] text-[hsl(var(--accent-foreground))]"><ShieldCheck className="size-3.5" /> The short version</div>
        <h1 className="text-4xl font-extrabold tracking-[-0.07em] sm:text-6xl">A clear desk has<br /><span className="text-[hsl(var(--accent-foreground))]">clear boundaries.</span></h1>
        <p className="mt-6 text-base leading-8 text-[hsl(var(--muted-foreground))]">Dropdesk is built for saving public media references, not for getting around private accounts, paywalls, or platform rules.</p>
      </div>
      <div className="mt-12 grid gap-4 md:grid-cols-[1.1fr_.9fr]">
        <div className="rise-in delay-1 rounded-2xl bg-[hsl(var(--primary))] p-7 text-[hsl(var(--primary-foreground))] sm:p-9">
          <LockKeyhole className="mb-10 size-7 text-[hsl(var(--accent))]" />
          <h2 className="text-2xl font-extrabold tracking-[-0.04em]">Public URLs only</h2>
          <p className="mt-4 text-sm leading-7 text-white/65">The downloader accepts links that are publicly reachable. We do not ask for social credentials, and we do not provide a way to access private or restricted media.</p>
        </div>
        <div className="rise-in delay-2 rounded-2xl border border-[hsl(var(--border))] bg-[hsl(var(--card))] p-7 sm:p-9">
          <CircleHelp className="mb-10 size-7 text-[hsl(var(--accent-foreground))]" />
          <h2 className="text-2xl font-extrabold tracking-[-0.04em]">Your responsibility</h2>
          <p className="mt-4 text-sm leading-7 text-[hsl(var(--muted-foreground))]">Only download media you own or have permission to keep. Follow the terms, copyright rules, and community guidelines that apply to the platform and creator.</p>
        </div>
      </div>
      <div className="rise-in delay-3 mt-12 divide-y divide-[hsl(var(--border))] border-y border-[hsl(var(--border))]">
        {[
          ['What we send', 'The public URL you paste and the format you choose are sent to our service to inspect and prepare the requested file.'],
          ['What we do not need', 'No social login, password, contact list, or personal profile information is needed to use the desk.'],
          ['How to use a saved file', 'Treat downloads like any other reference material: keep them private when required, credit creators, and do not redistribute without permission.'],
        ].map(([title, body]) => <div key={title} className="grid gap-2 py-6 sm:grid-cols-[190px_1fr] sm:gap-8"><h3 className="text-sm font-extrabold">{title}</h3><p className="text-sm leading-7 text-[hsl(var(--muted-foreground))]">{body}</p></div>)}
      </div>
      <div className="mt-10"><Link href="/" data-testid="link-back-to-desk" className="inline-flex items-center gap-2 rounded-xl bg-[hsl(var(--primary))] px-5 py-3 text-sm font-bold text-[hsl(var(--primary-foreground))] shadow-[3px_3px_0_hsl(var(--accent))]"><ArrowRight className="size-4 rotate-180" /> Back to the desk</Link></div>
    </div>
  );
}

function Router() {
  const [location] = useLocation();
  return (
    <ErrorBoundary resetKey={location}>
      <Shell>
        <Switch>
          <Route path="/" component={Home} />
          <Route path="/privacy" component={Privacy} />
          <Route component={NotFound} />
        </Switch>
      </Shell>
    </ErrorBoundary>
  );
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <WouterRouter base={import.meta.env.BASE_URL.replace(/\/$/, '')}>
          <Router />
        </WouterRouter>
        <Toaster />
      </TooltipProvider>
    </QueryClientProvider>
  );
}

export default App;