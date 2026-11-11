/* ==========================================================================
   Dynamic Island for Android — Interactive Website & Simulator Logic
   ========================================================================== */

(function () {
  "use strict";

  const REPO_OWNER = "Chaxupatel";
  const REPO_NAME = "dynamic-island";
  const RELEASES_URL = `https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/latest`;
  const RELEASE_API_URL = `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/latest`;

  // Exact defaults from IslandModels.kt -> defaultFeatureSizes()
  const DEFAULT_SIZES = {
    IDLE: { label: "Idle Pill", width: 114, height: 32, expandedWidth: 346 },
    NOTIFICATION: { label: "Notification", width: 286, height: 48, expandedWidth: 346 },
    MEDIA: { label: "Music", width: 190, height: 34, expandedWidth: 346 },
    CHARGING: { label: "Charging", width: 270, height: 46, expandedWidth: 336 },
    BLUETOOTH: { label: "Bluetooth", width: 286, height: 48, expandedWidth: 340 },
    TIMER: { label: "Timer", width: 176, height: 34, expandedWidth: 336 },
    CALL: { label: "Call", width: 196, height: 34, expandedWidth: 346 },
    RINGER: { label: "Silent / Ring", width: 184, height: 42, expandedWidth: 300 },
  };

  const TRACKS = [
    { title: "Midnight City", artist: "M83 · Hurry Up, We're Dreaming", duration: 243 },
    { title: "Nightcall", artist: "Kavinsky · OutRun", duration: 258 },
    { title: "Resonance", artist: "HOME · Odyssey", duration: 212 },
  ];

  const state = {
    theme: "obsidian",
    feature: "MEDIA",
    isSplit: false,
    isExpanded: false,
    isReplyOpen: false,
    replySentText: "",
    trackIndex: 0,
    mediaPlaying: true,
    mediaPos: 86,
    timerSeconds: 272,
    timerPaused: false,
    callSeconds: 94,
    ringerMode: "SILENT", // SILENT | VIBRATE | NORMAL
    globalOffsetX: 0,
    globalOffsetY: 8,
    globalCornerRadius: 26,
    sizes: structuredClone(DEFAULT_SIZES),
  };

  // DOM References
  const root = document.documentElement;
  const capsuleEl = document.getElementById("sim-capsule");
  const statusHintEl = document.getElementById("sim-hint");
  const selectedFeatureNameEl = document.getElementById("cal-feature-name");

  const sliderW = document.getElementById("cal-width");
  const sliderH = document.getElementById("cal-height");
  const sliderEW = document.getElementById("cal-expanded-width");
  const sliderX = document.getElementById("cal-offset-x");
  const sliderY = document.getElementById("cal-offset-y");
  const sliderR = document.getElementById("cal-corner-radius");
  const expandedRow = document.getElementById("cal-expanded-row");

  const valW = document.getElementById("val-width");
  const valH = document.getElementById("val-height");
  const valEW = document.getElementById("val-expanded-width");
  const valX = document.getElementById("val-offset-x");
  const valY = document.getElementById("val-offset-y");
  const valR = document.getElementById("val-corner-radius");

  function fmtTime(sec) {
    const s = Math.max(0, Math.floor(sec));
    const m = Math.floor(s / 60);
    const rem = s % 60;
    return `${m}:${rem < 10 ? "0" : ""}${rem}`;
  }

  const THEME_META_COLORS = {
    obsidian: "#06070B",
    alabaster: "#F3EFE6",
    monolith: "#050506",
  };

  function applyTheme(themeKey) {
    state.theme = themeKey;
    root.setAttribute("data-theme", themeKey);
    const metaTheme = document.querySelector('meta[name="theme-color"]');
    if (metaTheme && THEME_META_COLORS[themeKey]) {
      metaTheme.setAttribute("content", THEME_META_COLORS[themeKey]);
    }
    try {
      localStorage.setItem("di_website_theme", themeKey);
    } catch (_) {}

    document.querySelectorAll("[data-set-theme]").forEach((btn) => {
      const active = btn.getAttribute("data-set-theme") === themeKey;
      btn.classList.toggle("active", active);
      btn.setAttribute("aria-pressed", String(active));
    });

    document.querySelectorAll(".theme-card[data-theme-card]").forEach((card) => {
      const active = card.getAttribute("data-theme-card") === themeKey;
      card.classList.toggle("active", active);
      const tag = card.querySelector(".theme-active-tag");
      if (tag) {
        tag.textContent = active ? "Active Theme" : "Select";
      }
    });

    renderCapsule();
  }

  function getAuraColor() {
    switch (state.feature) {
      case "MEDIA":
        return "var(--secondary)";
      case "CALL":
      case "CHARGING":
        return "var(--positive)";
      case "TIMER":
        return "var(--warning)";
      case "RINGER":
        return state.ringerMode === "SILENT"
          ? "var(--danger)"
          : state.ringerMode === "VIBRATE"
            ? "var(--warning)"
            : "var(--positive)";
      case "NOTIFICATION":
      case "BLUETOOTH":
        return "var(--primary)";
      default:
        return state.theme === "monolith" ? "var(--primary)" : "transparent";
    }
  }

  function renderCapsule() {
    if (!capsuleEl) return;
    const spec = state.sizes[state.feature] || DEFAULT_SIZES.MEDIA;
    const effectiveWidth =
      state.isSplit && !state.isExpanded ? Math.min(368, spec.width + 58) : spec.width;

    capsuleEl.style.setProperty("--sim-w", `${effectiveWidth}px`);
    capsuleEl.style.setProperty("--sim-h", `${spec.height}px`);
    capsuleEl.style.setProperty("--sim-ew", `${spec.expandedWidth}px`);
    capsuleEl.style.setProperty("--sim-radius", `${state.globalCornerRadius}px`);
    capsuleEl.style.setProperty("--sim-x", `${state.globalOffsetX * 0.45}px`);
    capsuleEl.style.setProperty("--sim-y", `${(state.globalOffsetY - 8) * 0.45}px`);
    capsuleEl.style.setProperty("--sim-aura", getAuraColor());

    capsuleEl.classList.toggle("is-expanded", state.isExpanded);

    if (state.isExpanded) {
      capsuleEl.innerHTML = buildExpandedMarkup();
      statusHintEl.textContent =
        "Expanded card · Tap header or background to collapse · Interactive controls live";
      wireExpandedEvents();
    } else {
      capsuleEl.innerHTML = buildCompactMarkup();
      statusHintEl.textContent =
        state.feature === "IDLE"
          ? "Idle capsule · Pick a live activity below or tap to expand"
          : "Tap capsule to expand · Try Dual Split-Pill or Calibration sliders below";
    }
  }

  function buildCompactMarkup() {
    const track = TRACKS[state.trackIndex];

    if (state.isSplit && state.feature === "MEDIA") {
      return `
        <div class="island-split-wrap">
          <div style="display:flex;align-items:center;gap:8px;min-width:0;">
            <span class="island-badge-circle" style="background:color-mix(in srgb, var(--secondary) 22%, transparent);color:var(--secondary);">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M12 3v10.55A4 4 0 1 0 14 17V7h4V3h-6z"/></svg>
            </span>
            <span class="island-compact-label">${track.title}</span>
          </div>
          <span class="island-secondary-chip tnum">
            <svg width="11" height="11" viewBox="0 0 24 24" fill="currentColor"><path d="M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61 1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42A8.962 8.962 0 0 0 12 4c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61z"/></svg>
            ${fmtTime(state.timerSeconds)}
          </span>
        </div>`;
    }

    switch (state.feature) {
      case "IDLE":
        if (state.theme === "monolith") {
          return `
            <div class="idle-pips">
              <span class="idle-bar"></span>
              <span class="idle-dot"></span>
            </div>`;
        }
        return `<div class="idle-pips" style="opacity:0;"></div>`;

      case "MEDIA":
        return `
          <div class="island-compact-row">
            <span class="island-badge-circle" style="background:color-mix(in srgb, var(--secondary) 20%, transparent);color:var(--secondary);">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M12 3v10.55A4 4 0 1 0 14 17V7h4V3h-6z"/></svg>
            </span>
            <span class="island-compact-label">${track.title}</span>
            <div class="waveform ${state.mediaPlaying ? "" : "paused"}" aria-hidden="true">
              <span></span><span></span><span></span><span></span><span></span>
            </div>
          </div>`;

      case "NOTIFICATION":
        return `
          <div class="island-compact-row">
            <span class="island-badge-circle" style="background:color-mix(in srgb, var(--primary) 22%, transparent);color:var(--primary);">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z"/></svg>
            </span>
            <span class="island-compact-label">Alex · Hey! Are you joining tonight?</span>
            <span class="island-compact-sub" style="color:var(--primary);">Reply</span>
          </div>`;

      case "CHARGING":
        return `
          <div class="island-compact-row">
            <span class="island-badge-circle" style="background:color-mix(in srgb, var(--positive) 22%, transparent);color:var(--positive);">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M13 2 4 14h7l-1 8 9-12h-7l1-8z"/></svg>
            </span>
            <span class="island-compact-label">Fast Charging · AC</span>
            <span class="island-compact-sub tnum" style="color:var(--positive);">84%</span>
          </div>`;

      case "BLUETOOTH":
        return `
          <div class="island-compact-row">
            <span class="island-badge-circle" style="background:color-mix(in srgb, var(--primary) 22%, transparent);color:var(--primary);">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M12 3a9 9 0 0 0-9 9v7c0 1.1.9 2 2 2h4v-8H5v-1c0-3.87 3.13-7 7-7s7 3.13 7 7v1h-4v8h4c1.1 0 2-.9 2-2v-7a9 9 0 0 0-9-9z"/></svg>
            </span>
            <span class="island-compact-label">Pixel Buds Pro Connected</span>
            <span class="island-compact-sub tnum" style="color:var(--secondary);">92%</span>
          </div>`;

      case "TIMER":
        return `
          <div class="island-compact-row">
            <span class="island-badge-circle" style="background:color-mix(in srgb, var(--warning) 22%, transparent);color:var(--warning);">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61 1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42A8.962 8.962 0 0 0 12 4c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61z"/></svg>
            </span>
            <span class="island-compact-label">Focus Timer</span>
            <span class="island-compact-sub tnum" style="color:var(--warning);">${fmtTime(state.timerSeconds)}</span>
          </div>`;

      case "CALL":
        return `
          <div class="island-compact-row">
            <span class="island-badge-circle" style="background:color-mix(in srgb, var(--positive) 22%, transparent);color:var(--positive);">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M6.62 10.79a15.05 15.05 0 0 0 6.59 6.59l2.2-2.2a1 1 0 0 1 1.01-.24c1.12.37 2.33.57 3.58.57a1 1 0 0 1 1 1V20a1 1 0 0 1-1 1C10.61 21 3 13.39 3 4a1 1 0 0 1 1-1h3.5a1 1 0 0 1 1 1c0 1.25.2 2.46.57 3.58a1 1 0 0 1-.25 1.01l-2.2 2.2z"/></svg>
            </span>
            <span class="island-compact-label">Maya Patel</span>
            <span class="island-compact-sub tnum" style="color:var(--positive);">${fmtTime(state.callSeconds)}</span>
          </div>`;

      case "RINGER": {
        const col =
          state.ringerMode === "SILENT"
            ? "var(--danger)"
            : state.ringerMode === "VIBRATE"
              ? "var(--warning)"
              : "var(--positive)";
        const text =
          state.ringerMode === "SILENT"
            ? "Silent"
            : state.ringerMode === "VIBRATE"
              ? "Vibrate"
              : "Ring On";
        return `
          <div class="island-compact-row">
            <span class="island-badge-circle" style="background:color-mix(in srgb, ${col} 22%, transparent);color:${col};">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"/></svg>
            </span>
            <span class="island-compact-label">Ringer Mode</span>
            <span class="island-compact-sub" style="color:${col};">${text}</span>
          </div>`;
      }
    }
  }

  function buildExpandedMarkup() {
    const track = TRACKS[state.trackIndex];
    const pct = Math.min(100, Math.round((state.mediaPos / track.duration) * 100));

    switch (state.feature) {
      case "MEDIA":
        return `
          <div class="island-expanded-card" data-stop-collapse>
            <div class="exp-header">
              <div class="exp-art" style="background:linear-gradient(135deg, var(--primary), var(--secondary));color:#fff;">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M12 3v10.55A4 4 0 1 0 14 17V7h4V3h-6z"/></svg>
              </div>
              <div class="exp-title-col">
                <div class="exp-title">${track.title}</div>
                <div class="exp-subtitle">${track.artist}</div>
              </div>
              <div class="waveform ${state.mediaPlaying ? "" : "paused"}">
                <span></span><span></span><span></span><span></span><span></span>
              </div>
            </div>
            <div>
              <div class="exp-progress-track" id="sim-seek-bar" title="Click to seek">
                <div class="exp-progress-fill" style="width:${pct}%;"></div>
              </div>
              <div style="display:flex;justify-content:space-between;margin-top:5px;font-size:0.7rem;color:var(--island-text-muted);" class="tnum">
                <span>${fmtTime(state.mediaPos)}</span>
                <span>${fmtTime(track.duration)}</span>
              </div>
            </div>
            <div class="exp-controls-row">
              <button class="exp-action-chip" id="sim-collapse-btn">Collapse</button>
              <div style="display:flex;align-items:center;gap:8px;">
                <button class="exp-icon-btn" id="sim-prev-btn" aria-label="Previous track">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M6 6h2v12H6zm3.5 6 8.5 6V6z"/></svg>
                </button>
                <button class="exp-icon-btn primary" id="sim-play-btn" aria-label="Play or pause">
                  ${
                    state.mediaPlaying
                      ? '<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>'
                      : '<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>'
                  }
                </button>
                <button class="exp-icon-btn" id="sim-next-btn" aria-label="Next track">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="m6 18 8.5-6L6 6v12zM16 6v12h2V6h-2z"/></svg>
                </button>
              </div>
            </div>
          </div>`;

      case "NOTIFICATION":
        return `
          <div class="island-expanded-card" data-stop-collapse>
            <div class="exp-header">
              <div class="exp-art" style="background:color-mix(in srgb, var(--primary) 22%, transparent);color:var(--primary);font-weight:800;">A</div>
              <div class="exp-title-col">
                <div class="exp-title">Alex Chen · Messages</div>
                <div class="exp-subtitle">Just now · Inline RemoteInput ready</div>
              </div>
              <button class="exp-action-chip" id="sim-collapse-btn">Done</button>
            </div>
            <div class="exp-body-text">
              ${
                state.replySentText
                  ? `<span style="color:var(--positive);font-weight:600;">Reply sent:</span> "${state.replySentText}"`
                  : "Hey! Are we still testing the Dynamic Island build on Android 15 tonight?"
              }
            </div>
            ${
              state.isReplyOpen
                ? `<div class="exp-reply-box">
                    <input class="exp-reply-input" id="sim-reply-input" type="text" placeholder="Type quick reply..." />
                    <button class="exp-action-chip primary" id="sim-send-reply">Send</button>
                  </div>`
                : `<div style="display:flex;gap:8px;">
                    <button class="exp-action-chip primary" id="sim-open-reply">Quick Reply</button>
                    <button class="exp-action-chip" id="sim-mark-read">Mark Read</button>
                  </div>`
            }
          </div>`;

      case "CHARGING":
        return `
          <div class="island-expanded-card" data-stop-collapse>
            <div class="exp-header">
              <div class="exp-art" style="background:color-mix(in srgb, var(--positive) 22%, transparent);color:var(--positive);">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M13 2 4 14h7l-1 8 9-12h-7l1-8z"/></svg>
              </div>
              <div class="exp-title-col">
                <div class="exp-title">Fast Charging · AC Adapter</div>
                <div class="exp-subtitle">84% · Approx. 18 min until full</div>
              </div>
              <span class="tnum" style="font-size:1.1rem;font-weight:800;color:var(--positive);">84%</span>
            </div>
            <div class="exp-progress-track">
              <div class="exp-progress-fill" style="width:84%;background:var(--positive);"></div>
            </div>
          </div>`;

      case "BLUETOOTH":
        return `
          <div class="island-expanded-card" data-stop-collapse>
            <div class="exp-header">
              <div class="exp-art" style="background:color-mix(in srgb, var(--primary) 22%, transparent);color:var(--primary);">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M12 3a9 9 0 0 0-9 9v7c0 1.1.9 2 2 2h4v-8H5v-1c0-3.87 3.13-7 7-7s7 3.13 7 7v1h-4v8h4c1.1 0 2-.9 2-2v-7a9 9 0 0 0-9-9z"/></svg>
              </div>
              <div class="exp-title-col">
                <div class="exp-title">Pixel Buds Pro</div>
                <div class="exp-subtitle">Bluetooth Audio Connected · Active</div>
              </div>
              <span class="tnum" style="font-size:1rem;font-weight:800;color:var(--secondary);">92%</span>
            </div>
            <div class="exp-progress-track">
              <div class="exp-progress-fill" style="width:92%;"></div>
            </div>
          </div>`;

      case "TIMER":
        return `
          <div class="island-expanded-card" data-stop-collapse>
            <div class="exp-header">
              <div class="exp-art" style="background:color-mix(in srgb, var(--warning) 22%, transparent);color:var(--warning);">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61 1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42A8.962 8.962 0 0 0 12 4c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61z"/></svg>
              </div>
              <div class="exp-title-col">
                <div class="exp-title">Focus Sprint Timer</div>
                <div class="exp-subtitle">${state.timerPaused ? "Paused" : "Counting down live"}</div>
              </div>
              <span class="tnum" style="font-size:1.15rem;font-weight:800;color:var(--warning);">${fmtTime(state.timerSeconds)}</span>
            </div>
            <div style="display:flex;gap:8px;justify-content:flex-end;">
              <button class="exp-action-chip" id="sim-timer-reset">Reset</button>
              <button class="exp-action-chip primary" id="sim-timer-toggle">${state.timerPaused ? "Resume" : "Pause"}</button>
            </div>
          </div>`;

      case "CALL":
        return `
          <div class="island-expanded-card" data-stop-collapse>
            <div class="exp-header">
              <div class="exp-art" style="background:color-mix(in srgb, var(--positive) 22%, transparent);color:var(--positive);font-weight:800;">MP</div>
              <div class="exp-title-col">
                <div class="exp-title">Maya Patel</div>
                <div class="exp-subtitle tnum">Ongoing Call · ${fmtTime(state.callSeconds)}</div>
              </div>
              <button class="exp-action-chip" style="background:var(--danger);color:#fff;" id="sim-collapse-btn">End</button>
            </div>
          </div>`;

      case "RINGER":
        return `
          <div class="island-expanded-card" data-stop-collapse>
            <div class="exp-header">
              <div class="exp-title-col">
                <div class="exp-title">Sound &amp; Ringer Profile</div>
                <div class="exp-subtitle">Tap a mode to preview popup behavior</div>
              </div>
            </div>
            <div style="display:flex;gap:8px;">
              <button class="exp-action-chip ${state.ringerMode === "SILENT" ? "primary" : ""}" data-ringer="SILENT">Silent</button>
              <button class="exp-action-chip ${state.ringerMode === "VIBRATE" ? "primary" : ""}" data-ringer="VIBRATE">Vibrate</button>
              <button class="exp-action-chip ${state.ringerMode === "NORMAL" ? "primary" : ""}" data-ringer="NORMAL">Normal</button>
            </div>
          </div>`;

      default:
        return `
          <div class="island-expanded-card" data-stop-collapse>
            <div class="exp-header">
              <div class="exp-title-col">
                <div class="exp-title">Dynamic Island Ready</div>
                <div class="exp-subtitle">Idle state · Waiting for live notifications, media, or system events</div>
              </div>
              <button class="exp-action-chip" id="sim-collapse-btn">Collapse</button>
            </div>
          </div>`;
    }
  }

  function wireExpandedEvents() {
    capsuleEl.querySelectorAll("[data-stop-collapse]").forEach((el) => {
      el.addEventListener("click", (e) => {
        if (e.target.closest("button, input, #sim-seek-bar")) {
          e.stopPropagation();
        }
      });
    });

    const collapseBtn = document.getElementById("sim-collapse-btn");
    if (collapseBtn) {
      collapseBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        state.isExpanded = false;
        state.isReplyOpen = false;
        renderCapsule();
      });
    }

    const playBtn = document.getElementById("sim-play-btn");
    if (playBtn) {
      playBtn.addEventListener("click", () => {
        state.mediaPlaying = !state.mediaPlaying;
        renderCapsule();
      });
    }

    const prevBtn = document.getElementById("sim-prev-btn");
    if (prevBtn) {
      prevBtn.addEventListener("click", () => {
        state.trackIndex = (state.trackIndex - 1 + TRACKS.length) % TRACKS.length;
        state.mediaPos = 18;
        renderCapsule();
      });
    }

    const nextBtn = document.getElementById("sim-next-btn");
    if (nextBtn) {
      nextBtn.addEventListener("click", () => {
        state.trackIndex = (state.trackIndex + 1) % TRACKS.length;
        state.mediaPos = 12;
        renderCapsule();
      });
    }

    const seekBar = document.getElementById("sim-seek-bar");
    if (seekBar) {
      seekBar.addEventListener("click", (e) => {
        const rect = seekBar.getBoundingClientRect();
        const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
        state.mediaPos = Math.round(TRACKS[state.trackIndex].duration * ratio);
        renderCapsule();
      });
    }

    const openReplyBtn = document.getElementById("sim-open-reply");
    if (openReplyBtn) {
      openReplyBtn.addEventListener("click", () => {
        state.isReplyOpen = true;
        renderCapsule();
        const input = document.getElementById("sim-reply-input");
        if (input) input.focus();
      });
    }

    const sendReplyBtn = document.getElementById("sim-send-reply");
    if (sendReplyBtn) {
      sendReplyBtn.addEventListener("click", () => {
        const input = document.getElementById("sim-reply-input");
        state.replySentText = (input && input.value.trim()) || "On my way!";
        state.isReplyOpen = false;
        renderCapsule();
      });
    }

    const markReadBtn = document.getElementById("sim-mark-read");
    if (markReadBtn) {
      markReadBtn.addEventListener("click", () => {
        state.isExpanded = false;
        state.feature = "IDLE";
        syncPreviewTileUI();
        renderCapsule();
      });
    }

    const timerToggle = document.getElementById("sim-timer-toggle");
    if (timerToggle) {
      timerToggle.addEventListener("click", () => {
        state.timerPaused = !state.timerPaused;
        renderCapsule();
      });
    }

    const timerReset = document.getElementById("sim-timer-reset");
    if (timerReset) {
      timerReset.addEventListener("click", () => {
        state.timerSeconds = 300;
        state.timerPaused = false;
        renderCapsule();
      });
    }

    capsuleEl.querySelectorAll("[data-ringer]").forEach((btn) => {
      btn.addEventListener("click", () => {
        state.ringerMode = btn.getAttribute("data-ringer");
        renderCapsule();
      });
    });
  }

  function syncPreviewTileUI() {
    document.querySelectorAll("[data-preview-feature]").forEach((btn) => {
      const f = btn.getAttribute("data-preview-feature");
      const splitBtn = btn.hasAttribute("data-split-mode");
      const isActive = splitBtn ? state.isSplit && state.feature === "MEDIA" : !state.isSplit && state.feature === f;
      btn.classList.toggle("active", isActive);
    });

    document.querySelectorAll("[data-cal-feature]").forEach((chip) => {
      chip.classList.toggle("active", chip.getAttribute("data-cal-feature") === state.feature);
    });
  }

  function syncCalibrationSliders() {
    const spec = state.sizes[state.feature];
    if (!spec) return;

    if (selectedFeatureNameEl) {
      selectedFeatureNameEl.textContent = spec.label;
    }

    sliderW.value = spec.width;
    valW.textContent = `${spec.width} dp`;

    sliderH.value = spec.height;
    valH.textContent = `${spec.height} dp`;

    sliderEW.value = spec.expandedWidth;
    valEW.textContent = `${spec.expandedWidth} dp`;
    expandedRow.style.display = state.feature === "IDLE" ? "none" : "grid";

    sliderX.value = state.globalOffsetX;
    valX.textContent = `${state.globalOffsetX} dp`;

    sliderY.value = state.globalOffsetY;
    valY.textContent = `${state.globalOffsetY} dp`;

    sliderR.value = state.globalCornerRadius;
    valR.textContent = `${state.globalCornerRadius} dp`;
  }

  function selectFeature(featureKey, splitMode = false) {
    state.feature = featureKey;
    state.isSplit = splitMode;
    state.isExpanded = false;
    state.isReplyOpen = false;
    syncPreviewTileUI();
    syncCalibrationSliders();
    renderCapsule();
  }

  // Event bindings
  if (capsuleEl) {
    capsuleEl.addEventListener("click", () => {
      if (state.feature === "IDLE") {
        selectFeature("MEDIA", false);
        return;
      }
      state.isExpanded = !state.isExpanded;
      state.isReplyOpen = false;
      renderCapsule();
    });
  }

  document.querySelectorAll("[data-set-theme]").forEach((btn) => {
    btn.addEventListener("click", () => applyTheme(btn.getAttribute("data-set-theme")));
  });

  document.querySelectorAll(".theme-card[data-theme-card]").forEach((card) => {
    card.addEventListener("click", () => applyTheme(card.getAttribute("data-theme-card")));
  });

  document.querySelectorAll("[data-preview-feature]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const f = btn.getAttribute("data-preview-feature");
      const split = btn.hasAttribute("data-split-mode");
      selectFeature(f, split);
    });
  });

  document.querySelectorAll("[data-cal-feature]").forEach((chip) => {
    chip.addEventListener("click", () => {
      selectFeature(chip.getAttribute("data-cal-feature"), false);
    });
  });

  sliderW.addEventListener("input", () => {
    state.sizes[state.feature].width = Number(sliderW.value);
    valW.textContent = `${sliderW.value} dp`;
    renderCapsule();
  });

  sliderH.addEventListener("input", () => {
    state.sizes[state.feature].height = Number(sliderH.value);
    valH.textContent = `${sliderH.value} dp`;
    renderCapsule();
  });

  sliderEW.addEventListener("input", () => {
    state.sizes[state.feature].expandedWidth = Number(sliderEW.value);
    valEW.textContent = `${sliderEW.value} dp`;
    renderCapsule();
  });

  sliderX.addEventListener("input", () => {
    state.globalOffsetX = Number(sliderX.value);
    valX.textContent = `${sliderX.value} dp`;
    renderCapsule();
  });

  sliderY.addEventListener("input", () => {
    state.globalOffsetY = Number(sliderY.value);
    valY.textContent = `${sliderY.value} dp`;
    renderCapsule();
  });

  sliderR.addEventListener("input", () => {
    state.globalCornerRadius = Number(sliderR.value);
    valR.textContent = `${sliderR.value} dp`;
    renderCapsule();
  });

  const resetCalBtn = document.getElementById("reset-calibration");
  if (resetCalBtn) {
    resetCalBtn.addEventListener("click", () => {
      state.sizes = structuredClone(DEFAULT_SIZES);
      state.globalOffsetX = 0;
      state.globalOffsetY = 8;
      state.globalCornerRadius = 26;
      syncCalibrationSliders();
      renderCapsule();
    });
  }

  // Copy code buttons
  document.querySelectorAll("[data-copy-target]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const targetId = btn.getAttribute("data-copy-target");
      const codeEl = document.getElementById(targetId);
      if (!codeEl) return;
      try {
        await navigator.clipboard.writeText(codeEl.textContent.trim());
        const prev = btn.textContent;
        btn.textContent = "Copied!";
        setTimeout(() => {
          btn.textContent = prev;
        }, 1600);
      } catch (_) {}
    });
  });

  // Live ticking loop for media progress, countdown timer, and call chronometer
  setInterval(() => {
    let needsRender = false;
    if (state.mediaPlaying && state.feature === "MEDIA") {
      const track = TRACKS[state.trackIndex];
      state.mediaPos = (state.mediaPos + 1) % track.duration;
      needsRender = true;
    }
    if (!state.timerPaused && (state.feature === "TIMER" || state.isSplit)) {
      state.timerSeconds = state.timerSeconds > 0 ? state.timerSeconds - 1 : 300;
      needsRender = true;
    }
    if (state.feature === "CALL") {
      state.callSeconds += 1;
      needsRender = true;
    }
    if (needsRender && (!state.isReplyOpen || !state.isExpanded)) {
      renderCapsule();
    }
  }, 1000);

  // Resolve latest APK download from GitHub Releases API + optional local APK fallback
  async function resolveApkDownload() {
    const primaryBtn = document.getElementById("apk-download-primary");
    const heroBtn = document.getElementById("hero-download-btn");
    const versionBadge = document.getElementById("apk-version-val");
    const sizeBadge = document.getElementById("apk-size-val");
    const statusNote = document.getElementById("apk-source-note");
    const localBtn = document.getElementById("apk-local-btn");

    // 1. Check if a local APK is reachable (e.g., when opened via file:// or previewed from repo root)
    if (window.location.protocol === "file:" && localBtn) {
      localBtn.href = "../app/build/outputs/apk/debug/app-debug.apk";
      localBtn.setAttribute("download", "dynamic-island-v1.0.0-debug.apk");
      localBtn.style.display = "inline-flex";
      localBtn.textContent = "Direct Local APK (app-debug.apk)";
    } else {
      const localCandidates = [
        "./dynamic-island.apk",
        "../app/build/outputs/apk/debug/app-debug.apk",
      ];

      for (const candidate of localCandidates) {
        try {
          const res = await fetch(candidate, { method: "HEAD" });
          if (res.ok) {
            const bytes = Number(res.headers.get("content-length") || 0);
            const mb = bytes > 0 ? `${(bytes / (1024 * 1024)).toFixed(1)} MB` : "Local APK";
            if (localBtn) {
              localBtn.href = candidate;
              localBtn.setAttribute("download", "dynamic-island-v1.0.0-debug.apk");
              localBtn.style.display = "inline-flex";
              localBtn.textContent = `Direct Local APK (${mb})`;
            }
            if (sizeBadge) sizeBadge.textContent = mb;
            break;
          }
        } catch (_) {}
      }
    }

    // 2. Query GitHub Releases API for published .apk asset
    try {
      const response = await fetch(RELEASE_API_URL, {
        headers: { Accept: "application/vnd.github+json" },
      });
      if (!response.ok) throw new Error("No published release yet");
      const release = await response.json();
      const apkAsset = Array.isArray(release.assets)
        ? release.assets.find((a) => a.name && a.name.toLowerCase().endsWith(".apk"))
        : null;

      if (versionBadge && release.tag_name) {
        versionBadge.textContent = release.tag_name;
      }

      if (apkAsset && apkAsset.browser_download_url) {
        const mb = `${(apkAsset.size / (1024 * 1024)).toFixed(1)} MB`;
        if (sizeBadge) sizeBadge.textContent = mb;
        [primaryBtn, heroBtn].forEach((btn) => {
          if (!btn) return;
          btn.href = apkAsset.browser_download_url;
          btn.setAttribute("download", apkAsset.name);
        });
        if (primaryBtn) {
          primaryBtn.innerHTML = `
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M5 20h14v-2H5v2zm14-9h-4V3H9v8H5l7 7 7-7z"/></svg>
            Download ${apkAsset.name} (${mb})`;
        }
        if (statusNote) {
          statusNote.textContent = `Direct APK download fetched from GitHub Release ${release.tag_name}.`;
        }
        return;
      }
    } catch (_) {
      // Fall back gracefully to GitHub Releases page
      [primaryBtn, heroBtn].forEach((btn) => {
        if (!btn) return;
        btn.href = RELEASES_URL;
      });
      if (statusNote) {
        statusNote.textContent =
          "Links to GitHub Releases (latest). Or use the Direct Local APK / Gradle build instructions below.";
      }
    }
  }

  // Init
  let savedTheme = "obsidian";
  try {
    const stored = localStorage.getItem("di_website_theme");
    if (stored === "obsidian" || stored === "alabaster" || stored === "monolith") {
      savedTheme = stored;
    }
  } catch (_) {}

  applyTheme(savedTheme);
  syncPreviewTileUI();
  syncCalibrationSliders();
  renderCapsule();
  resolveApkDownload();
})();
