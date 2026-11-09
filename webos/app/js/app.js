/* Vision+ webOS - cliente ligero. Sin frameworks: D-pad, catálogo VPS y proxy local. */
(function () {
  "use strict";

  var CFG = window.VISION_CONFIG || {};
  var SERVICE_ID = CFG.serviceId || "com.chronosbvrx.visionplus.webos.service";
  var VERSION = CFG.version || "0.1.0";
  var PC = (function () {
    try {
      if (/(?:\?|&)platform=pc(?:&|$)/.test(String(location.search || ""))) return true;
      return /VisionPC|Vision\+ PC/i.test(String(navigator.userAgent || ""));
    } catch (error) {
      return false;
    }
  })();
  // Solo para probar la app de LG en Chrome: mantiene la interfaz webOS y
  // fuerza el relay porque el navegador no reproduce TS/HEVC.
  var RELAY_TEST = (function () {
    try {
      return /(?:\?|&)relay=1(?:&|$)/.test(String(location.search || ""));
    } catch (error) {
      return false;
    }
  })();

  // Pruebas en el webOS TV Simulator: permite apuntar la app al dev-server
  // HTTP en lugar del servicio Luna, y activar el relay si el simulador no
  // reproduce HEVC. Se pasa en los launchParams de ares-launch.
  (function applyLaunchOverrides() {
    var launch = {};
    try {
      var raw = window.PalmSystem && window.PalmSystem.launchParams;
      launch = raw ? JSON.parse(raw) : {};
    } catch (error) {
      launch = {};
    }
    if (launch && launch.devService && /^http:\/\/(?:127\.0\.0\.1|localhost)(?::\d+)?(?:\/|$)/i.test(String(launch.devService))) {
      CFG.devServiceUrl = String(launch.devService);
    }
    if (launch && launch.relay) RELAY_TEST = true;
  })();

  var state = {
    screen: "splash",
    stack: [],
    home: null,
    banners: [],
    popular: [],
    serviceInfo: null,
    search: { query: "", items: [], timer: null, focusResults: false, physical: false },
    detail: { film: null, seasons: [], seasonIndex: 0, episodes: [], loading: false },
    grid: { title: "", items: [], page: 1, pageSize: 120, total: 0, categoryId: "", loader: null },
    live: {
      page: 1,
      pageSize: 1035,
      items: [],
      total: 0,
      loading: false,
      epg: {},
      catalog: {},
      country: "México",
      category: "Todo",
      favorites: {},
    },
    sports: {
      tab: "events",
      events: [],
      agenda: [],
      matches: [],
      loading: false,
      sport: "principal",
      day: "today",
      league: "",
      favorites: {},
      selected: null,
      dialogReturn: null,
      statsCache: {},
    },
    matchCenter: {
      info: null,
      page: 1,
      stats: null,
      loading: false,
      error: "",
      returnFocus: null,
      requestKey: "",
    },
    player: {
      film: null,
      episode: null,
      season: null,
      isLive: false,
      duration: 0,
      subtitles: [],
      subtitleTracks: [],
      subtitleCues: [],
      subtitleIndex: -1,
      subtitleEnabled: true,
      availableTracks: [],
      trackIndex: 0,
      audioTracks: [],
      audioIndex: 0,
      markers: null,
      introSkipped: false,
      pendingSeek: 0,
      externalUrl: "",
      headers: {},
      sport: null,
      scrubRatio: null,
      forceRelay: false,
      relayBase: "",
      relayOffset: 0,
      retrying: false,
      retryHint: false,
      osdTimer: null,
      lastSave: 0,
      returnScreen: "home",
    },
    settings: { render: "direct", quality: "auto", subs: "es", skipIntro: "auto" },
    topTab: "",
    progress: {},
    list: {},
  };

  var $ = function (selector, root) {
    return (root || document).querySelector(selector);
  };
  var $$ = function (selector, root) {
    return Array.prototype.slice.call((root || document).querySelectorAll(selector));
  };

  function el(tag, className, html) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (html !== undefined && html !== null) node.innerHTML = html;
    return node;
  }

  function escapeHtml(value) {
    return String(value === undefined || value === null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function readJson(key, fallback) {
    try {
      var raw = window.localStorage.getItem(key);
      return raw ? JSON.parse(raw) : fallback;
    } catch (error) {
      return fallback;
    }
  }

  function writeJson(key, value) {
    try {
      window.localStorage.setItem(key, JSON.stringify(value));
    } catch (error) {
      /* la tele puede tener el almacenamiento lleno; no se rompe la UI */
    }
  }

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function formatTime(seconds) {
    var total = Math.max(0, Math.floor(Number(seconds) || 0));
    var h = Math.floor(total / 3600);
    var m = Math.floor((total % 3600) / 60);
    var s = total % 60;
    if (h > 0) return h + ":" + String(m).padStart(2, "0") + ":" + String(s).padStart(2, "0");
    return String(m).padStart(2, "0") + ":" + String(s).padStart(2, "0");
  }

  function formatDuration(minutes) {
    var value = Number(minutes) || 0;
    if (!value) return "";
    var h = Math.floor(value / 60);
    var m = value % 60;
    if (h && m) return h + " h " + m + " min";
    if (h) return h + " h";
    return m + " min";
  }

  function kindLabel(film) {
    if (!film) return "";
    if (isLive(film)) return "En vivo";
    if ((film.kind === "anime" || film.kind === "kids") && looksLikeMovie(film)) return "Película";
    if (film.kind === "series") return "Serie";
    if (film.kind === "anime") return "Anime";
    if (film.kind === "kids") return "Infantil";
    return "Película";
  }

  function isLive(film) {
    return Boolean(
      film &&
        (film.kind === "live" ||
          String(film.videoUrl || "").indexOf("xtvlive:") === 0 ||
          String(film.id || "").indexOf("xtv-live-") === 0),
    );
  }

  function isSeries(film) {
    if (!film || isLive(film) || film.kind === "movie") return false;
    if (Array.isArray(film.seasons) && film.seasons.length) return true;
    if (film.kind === "series" && Number(film.durationMin) > 0) return false;
    return film.kind === "series";
  }

  function posterOf(film) {
    return (
      (film && (film.posterUrl || film.poster || film.landscape || film.backdrop || film.backdropUrl)) ||
      "images/icon.png"
    );
  }

  function backdropOf(film) {
    return (film && (film.backdrop || film.backdropUrl || film.posterUrl || film.poster || film.landscape)) || "images/icon.png";
  }

  function scoreOf(film) {
    var score = Number(film && film.score);
    return score > 0 ? score.toFixed(1) : "";
  }

  function contentIdOf(film) {
    var ref = String((film && (film.videoUrl || film.id)) || "");
    return ref.replace(/^xtv:/, "").replace(/^magisvod:/, "").replace(/^magis:/, "");
  }

  function filmKey(film) {
    return [String((film && film.title) || "").trim().toLowerCase(), (film && film.year) || "", (film && film.kind) || ""].join("|");
  }

  function uniqueFilms(films) {
    var seen = {};
    return (films || []).filter(function (film) {
      if (!film) return false;
      var key = filmKey(film);
      if (seen[key]) return false;
      seen[key] = true;
      return true;
    });
  }

  function channelCodeOf(film) {
    var ref = String((film && (film.videoUrl || film.id)) || "");
    return ref.replace(/^xtvlive:/, "");
  }

  function todayMexico() {
    try {
      var parts = new Intl.DateTimeFormat("en-CA", {
        timeZone: "America/Mexico_City",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      }).formatToParts(new Date());
      var map = {};
      parts.forEach(function (part) {
        map[part.type] = part.value;
      });
      return map.year + "-" + map.month + "-" + map.day;
    } catch (error) {
      return new Date().toISOString().slice(0, 10);
    }
  }

  function eventDateLabel(event) {
    var bits = [];
    if (event.date) bits.push(event.date);
    if (event.time) bits.push(String(event.time).slice(0, 5));
    if (event.category) bits.push(event.category);
    return bits.join(" · ");
  }

  function playableLinks(event) {
    return ((event && event.links) || []).filter(function (link) {
      if (!link || !link.url || link.alive === false) return false;
      if (link.drm) return false;
      return String(link.type || "").toLowerCase() === "hls" || /\.m3u8(\?|$)/i.test(link.url);
    });
  }

  function progressKey(film, episode) {
    if (episode && episode.contentId) return String(episode.contentId);
    if (film && film.id) return String(film.id);
    return "";
  }

  function getProgress(film, episode) {
    var key = progressKey(film, episode);
    return (key && state.progress[key]) || (film && state.progress[film.id]) || null;
  }

  function serviceBase() {
    if (CFG.devServiceUrl) return String(CFG.devServiceUrl).replace(/\/+$/, "");
    if (typeof location !== "undefined" && /^https?:$/.test(location.protocol)) return location.origin;
    return "";
  }

  function request(method, params, timeout) {
    return new Promise(function (resolve, reject) {
      var payload = params || {};
      var usedLuna = false;
      // devServiceUrl es explícito: se usa el servidor HTTP aunque el
      // simulador inyecte las APIs de Luna.
      var forceHttp = Boolean(CFG.devServiceUrl);
      if (!forceHttp && window.webOS && window.webOS.service && window.webOS.service.request && window.PalmServiceBridge) {
        usedLuna = true;
        try {
          window.webOS.service.request("luna://" + SERVICE_ID, {
            method: method,
            parameters: payload,
            timeout: timeout || 45000,
            onSuccess: function (response) {
              if (response && response.returnValue === false) reject(new Error(response.errorText || "Error del servicio"));
              else resolve(response || {});
            },
            onFailure: function (response) {
              reject(new Error((response && (response.errorText || response.errorCode)) || "Servicio no disponible"));
            },
          });
        } catch (error) {
          reject(error);
        }
      }
      if (usedLuna) return;

      if (!forceHttp && window.PalmServiceBridge) {
        try {
          var bridge = new window.PalmServiceBridge();
          var settled = false;
          bridge.onservicecallback = function (raw) {
            if (settled) return;
            settled = true;
            var response = {};
            try {
              response = JSON.parse(raw);
            } catch (error) {
              response = { returnValue: false, errorText: String(raw) };
            }
            if (response && response.returnValue === false) reject(new Error(response.errorText || "Error del servicio"));
            else resolve(response || {});
          };
          bridge.call("luna://" + SERVICE_ID + "/" + method, JSON.stringify(payload));
        } catch (error) {
          reject(error);
        }
        return;
      }

      var base = serviceBase();
      if (!base) {
        reject(new Error("El servicio de Vision+ no está disponible. Abre la app desde webOS o usa el servidor de desarrollo."));
        return;
      }
      var controller = typeof AbortController === "function" ? new AbortController() : null;
      var timer = setTimeout(function () {
        if (controller) controller.abort();
      }, timeout || 45000);
      fetch(base + "/" + method, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
        signal: controller ? controller.signal : undefined,
      })
        .then(function (response) {
          return response.json();
        })
        .then(function (response) {
          clearTimeout(timer);
          if (response && response.returnValue === false) reject(new Error(response.errorText || "Error del servicio"));
          else resolve(response || {});
        })
        .catch(function (error) {
          clearTimeout(timer);
          reject(error);
        });
    });
  }

  /* ============================ foco / D-pad ============================ */

  var Focus = {
    current: null,
    set: function (node, options) {
      if (!node) return;
      var owner = node.closest ? node.closest(".screen") : null;
      if (owner && !owner.classList.contains("is-active")) return;
      if (this.current && this.current !== node) this.current.classList.remove("focused");
      this.current = node;
      node.classList.add("focused");
      try {
        node.scrollIntoView({ block: "nearest", inline: "center", behavior: "smooth" });
      } catch (error) {
        try {
          node.scrollIntoView(false);
        } catch (ignored) {
          /* noop */
        }
      }
      if (options && options.silent) return;
    },
    clear: function () {
      if (this.current) this.current.classList.remove("focused");
      this.current = null;
    },
    visible: function (node) {
      if (!node || node.disabled || node.hidden) return false;
      if (node.closest && node.closest(".player-osd") && !$("#screen-player").classList.contains("show-osd")) return false;
      if (node.closest && node.closest(".player-top") && !$("#screen-player").classList.contains("show-osd")) return false;
      var rect = node.getBoundingClientRect();
      if (rect.width < 2 || rect.height < 2) return false;
      var style = window.getComputedStyle(node);
      return style.visibility !== "hidden" && style.display !== "none" && style.opacity !== "0";
    },
    candidates: function () {
      var dialog = $(".dialog:not([hidden])");
      if (dialog) return $$("[data-focusable]", dialog).filter(Focus.visible);
      var menu = $(".vt-menu:not([hidden])");
      if (menu) return $$("[data-focusable]", menu).filter(Focus.visible);
      var screen = $(".screen.is-active");
      var bar = $("#app-topbar:not(.vt-hidden)");
      var list = bar ? $$("[data-focusable]", bar).filter(Focus.visible) : [];
      if (!screen) return list;
      return list.concat($$("[data-focusable]", screen).filter(Focus.visible));
    },
    first: function (selector) {
      var screen = $(".screen.is-active");
      if (!screen) return null;
      var node = selector ? $(selector, screen) : null;
      if (!node) node = $$("[data-focusable]", screen).filter(Focus.visible)[0] || null;
      if (node) Focus.set(node);
      return node;
    },
    move: function (direction) {
      var current = this.current;
      if (!current || !document.contains(current)) {
        this.first();
        return;
      }
      var base = current.getBoundingClientRect();
      var baseX = base.left + base.width / 2;
      var baseY = base.top + base.height / 2;
      var best = null;
      var bestScore = Infinity;
      var candidates = this.candidates();
      for (var i = 0; i < candidates.length; i += 1) {
        var node = candidates[i];
        if (node === current) continue;
        var rect = node.getBoundingClientRect();
        var x = rect.left + rect.width / 2;
        var y = rect.top + rect.height / 2;
        var dx = x - baseX;
        var dy = y - baseY;
        var primary;
        var secondary;
        if (direction === "left") {
          if (dx > -6) continue;
          primary = -dx;
          secondary = Math.abs(dy);
        } else if (direction === "right") {
          if (dx < 6) continue;
          primary = dx;
          secondary = Math.abs(dy);
        } else if (direction === "up") {
          if (dy > -6) continue;
          primary = -dy;
          secondary = Math.abs(dx);
        } else {
          if (dy < 6) continue;
          primary = dy;
          secondary = Math.abs(dx);
        }
        var overlapX = Math.max(0, Math.min(base.right, rect.right) - Math.max(base.left, rect.left));
        var overlapY = Math.max(0, Math.min(base.bottom, rect.bottom) - Math.max(base.top, rect.top));
        var aligned = direction === "left" || direction === "right" ? overlapY > Math.min(base.height, rect.height) * 0.35 : overlapX > Math.min(base.width, rect.width) * 0.28;
        var score = primary + secondary * (aligned ? 1.2 : 3.2) - (aligned ? 80 : 0);
        if (score < bestScore) {
          bestScore = score;
          best = node;
        }
      }
      if (best) Focus.set(best);
    },
    activate: function () {
      if (!this.current) {
        this.first();
        return;
      }
      try {
        this.current.click();
      } catch (error) {
        /* noop */
      }
    },
  };

  /* ============================ pantallas ============================ */

  function activeScreen() {
    return $("#screen-" + state.screen) || null;
  }

  function debugLog(message) {
    try {
      window.__vpLogs = window.__vpLogs || [];
      window.__vpLogs.push(message);
      if (window.__vpLogs.length > 80) window.__vpLogs.shift();
    } catch (error) {
      /* noop */
    }
  }

  function showScreen(name, options) {
    var opts = options || {};
    var previous = state.screen;
    debugLog("showScreen " + name + " from " + previous);
    if (name === previous) {
      if (opts.focus) Focus.set(opts.focus);
      return;
    }
    if (previous && previous !== "splash" && !opts.noStack) {
      state.stack.push({ name: previous, focus: Focus.current });
      if (state.stack.length > 24) state.stack.shift();
    }
    $$(".screen").forEach(function (screen) {
      screen.classList.remove("is-active");
    });
    var target = $("#screen-" + name);
    if (target) target.classList.add("is-active");
    state.screen = name;
    updateTopbar(name);
    Focus.clear();
    if (opts.focus) {
      setTimeout(function () {
        Focus.set(opts.focus);
      }, 30);
    } else if (name !== "splash" && name !== "player") {
      setTimeout(function () {
        Focus.first(opts.focusSelector);
      }, 30);
    }
  }

  function focusInTopbar() {
    return Boolean(Focus.current && Focus.current.closest && Focus.current.closest("#app-topbar"));
  }

  function goBack() {
    if ($("#match-center-dialog") && !$("#match-center-dialog").hidden) {
      closeMatchCenter();
      return;
    }
    if (state.screen === "sports" && !$("#sports-dialog").hidden) {
      closeSportsDialog();
      return;
    }
    if (state.screen === "player") {
      closePlayer();
      return;
    }
    if (state.screen === "search" && state.search.query) {
      clearSearch();
      return;
    }
    if ((state.screen === "home" || state.screen === "live" || state.screen === "sports") && !focusInTopbar()) {
      var activeTab = $("#app-nav .vt-tab.is-active") || $("#app-nav .vt-tab");
      if (activeTab) {
        Focus.set(activeTab);
        return;
      }
    }
    if (state.screen === "home") {
      if (window.PalmSystem && window.PalmSystem.platformBack) window.PalmSystem.platformBack();
      return;
    }
    var previous = state.stack.pop();
    if (previous) {
      showScreen(previous.name, { noStack: true });
      if (previous.focus && document.contains(previous.focus)) {
        setTimeout(function () {
          Focus.set(previous.focus);
        }, 40);
      }
    } else {
      showScreen("home", { noStack: true });
    }
  }

  function toast(message) {
    var node = $("#toast");
    node.textContent = message;
    node.classList.add("show");
    clearTimeout(toast.timer);
    toast.timer = setTimeout(function () {
      node.classList.remove("show");
    }, 3200);
  }

  function showFullscreenHint() {
    if (!PC) return;
    var hint = $("#pc-fullscreen-hint");
    if (!hint) return;
    hint.hidden = false;
    requestAnimationFrame(function () { hint.classList.add("show"); });
    clearTimeout(showFullscreenHint.timer);
    showFullscreenHint.timer = setTimeout(function () {
      hint.classList.remove("show");
      setTimeout(function () { hint.hidden = true; }, 380);
    }, 9000);
  }

  function clockTick() {
    var node = $("#clock");
    if (!node) return;
    try {
      node.textContent = new Intl.DateTimeFormat("es-MX", {
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
        timeZone: "America/Mexico_City",
      }).format(new Date());
    } catch (error) {
      node.textContent = new Date().toTimeString().slice(0, 5);
    }
  }

  /* ============================ tarjetas ============================ */

  function cardBadge(film) {
    if (isLive(film)) return '<span class="card-badge live">EN VIVO</span>';
    if (isSeries(film)) return '<span class="card-badge">SERIE</span>';
    return '<span class="card-badge">HD</span>';
  }

  function createCard(film, options) {
    var opts = options || {};
    var card = el("button", "card" + (opts.landscape ? " landscape" : ""));
    card.setAttribute("data-focusable", "true");
    card.setAttribute("data-film-id", film.id || "");
    var poster = opts.landscape ? (film.backdrop || film.backdropUrl || film.posterUrl || posterOf(film)) : posterOf(film);
    var score = scoreOf(film);
    var meta = [];
    if (film.year) meta.push(film.year);
    if (isLive(film)) meta.push("En vivo");
    else meta.push(kindLabel(film));
    var progress = !opts.noProgress && !isLive(film) ? getProgress(film, opts.episode) : null;
    var progressPct = progress && progress.duration > 0 ? clamp((progress.position / progress.duration) * 100, 0, 100) : 0;
    var subText = opts.subText || meta.join(" · ");
    card.innerHTML =
      '<div class="card-poster">' +
      '<img loading="lazy" src="' + escapeHtml(poster) + '" alt="">' +
      cardBadge(film) +
      (score ? '<span class="card-score">' + escapeHtml(score) + "</span>" : "") +
      (progressPct > 1 ? '<div class="card-progress"><span style="width:' + progressPct.toFixed(1) + '%"></span></div>' : "") +
      "</div>" +
      '<div class="card-title">' + escapeHtml(film.title || "Sin título") + "</div>" +
      '<div class="card-sub">' + escapeHtml(subText) + "</div>";
    var image = $("img", card);
    if (image) {
      image.addEventListener("error", function () {
        image.style.display = "none";
      });
    }
    card.addEventListener("click", function () {
      if (opts.onSelect) opts.onSelect(film);
      else openFilm(film);
    });
    return card;
  }

  function createGridCard(film, options) {
    var opts = options || {};
    var card = el("button", "grid-card");
    card.setAttribute("data-focusable", "true");
    card.setAttribute("data-film-id", film.id || "");
    var poster = posterOf(film);
    var score = scoreOf(film);
    card.innerHTML =
      '<div class="card-poster">' +
      '<img loading="lazy" src="' + escapeHtml(poster) + '" alt="">' +
      cardBadge(film) +
      (score ? '<span class="card-score">' + escapeHtml(score) + "</span>" : "") +
      "</div>" +
      '<div class="card-title">' + escapeHtml(film.title || "Sin título") + "</div>" +
      '<div class="card-sub">' + escapeHtml([film.year, kindLabel(film)].filter(Boolean).join(" · ")) + "</div>";
    var image = $("img", card);
    if (image) {
      image.addEventListener("error", function () {
        image.style.display = "none";
      });
    }
    card.addEventListener("click", function () {
      if (opts.onSelect) opts.onSelect(film);
      else openFilm(film);
    });
    return card;
  }

  /* ============================ home ============================ */

  function loadHome() {
    var hero = $("#home-hero");
    var rows = $("#home-rows");
    var status = $("#home-status");
    hero.innerHTML = '<div class="hero-loading"><div class="spinner"></div><span>Cargando catálogo</span></div>';
    rows.innerHTML = "";
    status.textContent = "";
    Promise.all([
      request("home", { limit: CFG.limitFilms || 24 }, 45000),
      request("banners", {}, 25000).catch(function () {
        return { items: [] };
      }),
    ])
      .then(function (results) {
        var data = results[0];
        var bannerPayload = results[1] || {};
        state.home = data;
        state.banners = bannerPayload.items || [];
        renderHome(data, state.banners);
        var popular = [];
        (data.categories || []).some(function (category) {
          if (category.id === "tendencias" || category.id === "estrenos") {
            popular = (category.films || []).slice(0, 12);
            return true;
          }
          return false;
        });
        state.popular = popular;
        request("prewarm", {}, 30000).catch(function () {});
      })
      .catch(function (error) {
        hero.innerHTML =
          '<div class="hero-loading" style="flex-direction:column">' +
          '<div style="font-size:1.1vw;color:#fff;font-weight:700">No se pudo cargar el catálogo</div>' +
          '<div style="max-width:46vw;text-align:center">' + escapeHtml(error.message) + "</div>" +
          '<button class="primary-btn" data-focusable id="home-retry">Reintentar</button>' +
          "</div>";
        var retry = $("#home-retry");
        if (retry) retry.addEventListener("click", loadHome);
        Focus.first("#home-retry");
      });
  }

  function pickHero(categories) {
    var wanted = ["estrenos", "tendencias", "recomendados"];
    for (var i = 0; i < wanted.length; i += 1) {
      var category = categories.find(function (item) {
        return item.id === wanted[i];
      });
      if (category && category.films && category.films.length) return category.films[0];
    }
    for (var j = 0; j < categories.length; j += 1) {
      if (categories[j].films && categories[j].films.length) return categories[j].films[0];
    }
    return null;
  }

  function bannerHero(banners, categories) {
    if (!banners || !banners.length) return null;
    var banner = banners[0];
    var all = [];
    (categories || []).forEach(function (category) {
      all = all.concat(category.films || []);
    });
    var match = all.find(function (film) {
      return film.videoUrl === banner.videoUrl || film.id === banner.ref;
    });
    return Object.assign({}, match || {}, {
      id: banner.ref || banner.id || (match && match.id),
      title: banner.title || (match && match.title) || "",
      year: banner.year || (match && match.year) || 0,
      durationMin: banner.durationMin || (match && match.durationMin) || 0,
      synopsis: banner.synopsis || (match && match.synopsis) || "",
      posterUrl: banner.poster || (match && match.posterUrl) || "",
      backdrop: banner.backdrop || "",
      score: Number(banner.rating) || (match && match.score) || 0,
      kind: banner.series ? "series" : (match && match.kind) || "movie",
      videoUrl: banner.videoUrl || (match && match.videoUrl) || "",
      genres: (match && match.genres) || [],
      seasons: (match && match.seasons) || [],
      tmdbId: (match && match.tmdbId) || 0,
      eyebrow: banner.eyebrow || "",
      badge: banner.badge || "",
    });
  }

  function renderHero(film) {
    var hero = $("#home-hero");
    if (!film) {
      hero.innerHTML = '<div class="hero-loading"><span>Sin títulos disponibles</span></div>';
      return;
    }
    var meta = [];
    if (film.year) meta.push(escapeHtml(film.year));
    if (scoreOf(film)) meta.push('<span class="score">' + escapeHtml(scoreOf(film)) + "</span>");
    if (film.durationMin) meta.push(escapeHtml(formatDuration(film.durationMin)));
    var primaryLabel = isSeries(film) ? "Reproducir T1 E1" : isLive(film) ? "Ver en vivo" : "Reproducir";
    var eyebrow = film.eyebrow || (isLive(film) ? "DEPORTES · EN VIVO" : kindLabel(film) + " DESTACADA");
    if (!isLive(film) && eyebrow.indexOf("DESTACADO") < 0) eyebrow += " · DESTACADO HOY";
    var inList = Boolean(state.list[film.id]);
    hero.innerHTML =
      '<div class="hero-bg" style="background-image:url(' + escapeHtml(backdropOf(film)) + ')"></div>' +
      '<div class="hero-shade"></div>' +
      '<div class="hero-content">' +
      '<div class="hero-kicker">' + escapeHtml(eyebrow) + "</div>" +
      '<div class="hero-title">' + escapeHtml(film.title || "") + "</div>" +
      '<div class="hero-meta">' + meta.join('<span class="dot"></span>') + "</div>" +
      '<div class="hero-synopsis">' + escapeHtml(film.synopsis || "") + "</div>" +
      '<div class="hero-actions">' +
      '<button class="primary-btn" id="hero-play" data-focusable><svg class="icon"><use href="#i-play"/></svg><span>' + primaryLabel + "</span></button>" +
      '<button class="secondary-btn" id="hero-detail" data-focusable><svg class="icon"><use href="#i-info"/></svg><span>Detalles</span></button>' +
      '<button class="secondary-btn" id="hero-fav" data-focusable><svg class="icon"><use href="#i-star"/></svg><span>' + (inList ? "En Mi lista" : "Favorito") + "</span></button>" +
      "</div>" +
      "</div>";
    var background = $(".hero-bg", hero);
    if (background) {
      var probe = new Image();
      probe.onerror = function () {
        background.style.backgroundImage = "linear-gradient(120deg,#16233a,#0b1220)";
      };
      probe.src = backdropOf(film);
    }
    $("#hero-play").addEventListener("click", function () {
      if (isSeries(film)) openDetail(film);
      else if (isLive(film)) playLive(film);
      else playFilm(film);
    });
    $("#hero-detail").addEventListener("click", function () {
      openDetail(film);
    });
    $("#hero-fav").addEventListener("click", function () {
      toggleList(film);
      var label = $("#hero-fav span");
      if (label) label.textContent = state.list[film.id] ? "En Mi lista" : "Favorito";
    });
    if (state.screen === "home") Focus.set($("#hero-play"));
  }

  function renderHome(data, banners) {
    var categories = (data.categories || []).slice();
    var heroFilm = bannerHero(banners, categories) || pickHero(categories);
    renderHero(heroFilm);

    var rows = $("#home-rows");
    rows.innerHTML = "";
    var chosen = [];
    var seen = {};
    var special = categories.filter(function (category) {
      return category.kind === "special" && category.id !== "catalogo-completo";
    });
    var themes = categories.filter(function (category) {
      return category.kind === "theme";
    });
    var genres = categories.filter(function (category) {
      return category.kind === "genre";
    });
    var sources = categories.filter(function (category) {
      return category.kind === "source";
    });
    var catalog = categories.find(function (category) {
      return category.id === "catalogo-completo";
    });

    function add(category, limit) {
      if (!category || !category.films || !category.films.length || seen[category.id]) return;
      seen[category.id] = true;
      chosen.push({ category: category, films: category.films.slice(0, limit || 24) });
    }

    var byId = {};
    categories.forEach(function (category) {
      byId[category.id] = category;
    });
    add(byId.tendencias);
    add(byId.estrenos);
    add(byId.recomendados);
    add(byId["tv-en-vivo"]);
    themes.slice(0, 4).forEach(function (category) {
      add(category, 12);
    });
    genres.slice(0, 8).forEach(function (category) {
      add(category, 12);
    });
    sources.slice(0, 4).forEach(function (category) {
      add(category, 12);
    });
    add(catalog, 24);

    var continueItems = Object.keys(state.progress)
      .map(function (key) {
        return state.progress[key];
      })
      .filter(function (entry) {
        return entry && entry.position > 20 && entry.duration > 0 && entry.position < entry.duration * 0.96 && entry.film;
      })
      .sort(function (a, b) {
        return (b.updatedAt || 0) - (a.updatedAt || 0);
      })
      .slice(0, 18);
    if (continueItems.length) {
      var row = buildContinueRow(continueItems);
      rows.appendChild(row);
    }

    var listItems = Object.keys(state.list || {})
      .map(function (key) {
        return state.list[key];
      })
      .filter(Boolean)
      .slice(0, 18);
    if (listItems.length) {
      rows.appendChild(buildSimpleRow("Mi lista", "", listItems, null));
    }

    chosen.forEach(function (entry) {
      rows.appendChild(buildCategoryRow(entry.category, entry.films));
    });

    var status = $("#home-status");
    if (status) {
      var count = 0;
      chosen.forEach(function (entry) {
        count += entry.films.length;
      });
      status.textContent = (data.updatedAt ? "Actualizado " + formatUpdated(data.updatedAt) + " · " : "") + count + " títulos en pantalla";
    }
  }

  function formatUpdated(raw) {
    try {
      var date = new Date(raw);
      if (Number.isNaN(date.getTime())) return "";
      return new Intl.DateTimeFormat("es-MX", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }).format(date);
    } catch (error) {
      return "";
    }
  }

  function buildCategoryRow(category, films) {
    var row = el("section", "row");
    var head = el("div", "row-head");
    head.innerHTML =
      '<div class="row-title">' + escapeHtml(category.title || category.id) + "</div>" +
      (category.kind === "genre" ? '<div class="row-sub">Género</div>' : "") +
      '<button class="row-more" data-focusable><span>Ver todo</span><svg class="icon"><use href="#i-chevron"/></svg></button>';
    var track = el("div", "row-track");
    uniqueFilms(films).forEach(function (film) {
      track.appendChild(createCard(film));
    });
    row.appendChild(head);
    row.appendChild(track);
    var more = $(".row-more", head);
    var canExpand =
      category.id === "catalogo-completo" ||
      category.id === "tv-en-vivo" ||
      category.kind === "theme" ||
      category.kind === "genre" ||
      category.kind === "source";
    if (!canExpand) {
      more.hidden = true;
    } else {
      more.addEventListener("click", function () {
        if (category.id === "tv-en-vivo") {
          openLive();
          return;
        }
        openGrid({
          title: category.title || category.id,
          categoryId: category.id,
        });
      });
    }
    return row;
  }

  function buildSimpleRow(title, subtitle, films, onSelect) {
    var row = el("section", "row");
    var head = el("div", "row-head");
    head.innerHTML = '<div class="row-title">' + escapeHtml(title) + "</div>" + (subtitle ? '<div class="row-sub">' + escapeHtml(subtitle) + "</div>" : "");
    var track = el("div", "row-track");
    films.forEach(function (film) {
      track.appendChild(createCard(film, { onSelect: onSelect }));
    });
    row.appendChild(head);
    row.appendChild(track);
    return row;
  }

  function buildContinueRow(entries) {
    var row = el("section", "row continue-row");
    var head = el("div", "row-head");
    head.innerHTML = '<div class="row-title">Seguir viendo</div>';
    var track = el("div", "row-track");
    entries.forEach(function (entry) {
      var film = entry.film;
      var episode = entry.episode;
      var remaining = Math.max(0, (entry.duration || 0) - (entry.position || 0));
      var card = createCard(film, {
        episode: episode,
        landscape: true,
        subText: episode ? "T" + ((entry.season && entry.season.n) || 1) + " E" + (episode.number || "") + " · " + formatTime(remaining) + " restantes" : formatTime(remaining) + " restantes",
        onSelect: function () {
          playFilm(film, episode, entry.season || null);
        },
      });
      track.appendChild(card);
    });
    row.appendChild(head);
    row.appendChild(track);
    return row;
  }

  /* ============================ detalle ============================ */

  function openFilm(film) {
    if (isLive(film)) playLive(film);
    else openDetail(film);
  }

  function openDetail(film) {
    if (!film) return;
    state.detail.film = film;
    state.detail.seasons = Array.isArray(film.seasons) ? film.seasons.slice() : [];
    state.detail.seasonIndex = 0;
    state.detail.episodes = [];
    state.detail.loading = false;
    var detailScroll = $("#screen-detail .detail-content");
    if (detailScroll) detailScroll.scrollTop = 0;

    var backdrop = $("#detail-backdrop");
    backdrop.style.backgroundImage = "url(" + backdropOf(film) + ")";
    $("#detail-poster").src = posterOf(film);
    $("#detail-poster").onerror = function () {
      this.src = "images/icon.png";
    };
    $("#detail-score").textContent = scoreOf(film) || "";
    $("#detail-score").hidden = !scoreOf(film);
    $("#detail-kicker").textContent = kindLabel(film) + (film.tmdbId ? " · TMDB" : "");
    $("#detail-title").textContent = film.title || "";
    var meta = [];
    if (film.year) meta.push('<span>' + escapeHtml(film.year) + "</span>");
    if (film.durationMin) meta.push('<span>' + escapeHtml(formatDuration(film.durationMin)) + "</span>");
    if (scoreOf(film)) meta.push('<span class="gold">' + escapeHtml(scoreOf(film)) + "</span>");
    (film.genres || []).slice(0, 4).forEach(function (genre) {
      meta.push('<span class="pill">' + escapeHtml(genre) + "</span>");
    });
    if (film.kind) meta.push('<span class="pill">' + escapeHtml(kindLabel(film)) + "</span>");
    $("#detail-meta").innerHTML = meta.join("");
    $("#detail-synopsis").textContent = film.synopsis || "Sin sinopsis disponible.";
    $("#detail-play-label").textContent = isSeries(film) ? "Reproducir T1 E1" : "Reproducir";
    $("#detail-list-label").textContent = state.list[film.id] ? "Quitar de Mi lista" : "Mi lista";
    $("#detail-seasons").innerHTML = "";
    $("#detail-episodes").innerHTML = "";
    $("#detail-foot").textContent = "";

    $("#detail-play").onclick = function () {
      if (isSeries(film)) {
        if (state.detail.episodes.length) {
          playFilm(film, state.detail.episodes[0], state.detail.seasons[state.detail.seasonIndex] || null);
        } else {
          loadEpisodes(state.detail.seasons[0] || { titleId: film.id, n: 1 }).then(function () {
            if (state.detail.episodes.length) {
              playFilm(film, state.detail.episodes[0], state.detail.seasons[state.detail.seasonIndex] || null);
            } else {
              toast("No hay capítulos disponibles para esta serie.");
            }
          });
        }
      } else {
        playFilm(film);
      }
    };
    $("#detail-list").onclick = function () {
      toggleList(film);
    };
    $("#detail-info-btn").onclick = function () {
      var bits = [];
      if (film.synopsis) bits.push(film.synopsis);
      if (film.director) bits.push("Dirección: " + film.director);
      if (film.actors) bits.push("Reparto: " + film.actors);
      if (film.originalTitle) bits.push("Título original: " + film.originalTitle);
      toast(bits.join(" · ") || "Sin información adicional.");
    };

    showScreen("detail");
    renderSeasons();
    if (isSeries(film)) {
      loadEpisodes(state.detail.seasons[0] || { titleId: film.id, n: 1 });
    } else {
      $("#detail-seasons").innerHTML = "";
      $("#detail-episodes").innerHTML = "";
    }
    setTimeout(function () {
      Focus.first("#detail-play");
    }, 40);
  }

  function toggleList(film) {
    if (state.list[film.id]) {
      delete state.list[film.id];
      toast("Se quitó de Mi lista.");
    } else {
      state.list[film.id] = film;
      toast("Se agregó a Mi lista.");
    }
    writeJson("vp_webos_list", state.list);
    $("#detail-list-label").textContent = state.list[film.id] ? "Quitar de Mi lista" : "Mi lista";
  }

  function renderSeasons() {
    var holder = $("#detail-seasons");
    holder.innerHTML = "";
    var seasons = state.detail.seasons || [];
    if (!isSeries(state.detail.film) || seasons.length <= 1) return;
    seasons.forEach(function (season, index) {
      var chip = el("button", "season-chip" + (index === state.detail.seasonIndex ? " is-active" : ""));
      chip.setAttribute("data-focusable", "true");
      chip.textContent = "Temporada " + (season.n || index + 1);
      chip.addEventListener("click", function () {
        state.detail.seasonIndex = index;
        renderSeasons();
        loadEpisodes(season);
      });
      holder.appendChild(chip);
    });
  }

  function loadEpisodes(season, retried) {
    var film = state.detail.film;
    if (!film) return Promise.resolve([]);
    var target = season || { titleId: film.id, n: 1 };
    state.detail.loading = true;
    $("#detail-episodes").innerHTML = '<div class="episode-row"><div class="episode-info"><div class="episode-title">Cargando capítulos…</div></div></div>';
    return request("episodes", { ref: target.titleId || film.id }, 60000)
      .then(function (payload) {
        var episodes = (payload.episodes || []).map(function (episode, index) {
          return {
            contentId: episode.contentId,
            name: episode.name,
            title: episode.title || "",
            number: episode.seriesNumber || episode.episodeNumber || index + 1,
            overview: episode.overview || "",
            still: episode.still || "",
            duration: episode.duration || "",
            season: target,
          };
        });
        state.detail.episodes = episodes;
        state.detail.loading = false;
        if (payload.seasons && payload.seasons.length && !state.detail.seasons.length) {
          state.detail.seasons = payload.seasons;
          renderSeasons();
        }
        renderEpisodes();
        if (episodes.length) {
          $("#detail-play-label").textContent = "Reproducir T" + (target.n || 1) + " E" + (episodes[0].number || 1);
        }
        return episodes;
      })
      .catch(function (error) {
        state.detail.loading = false;
        var message = String((error && error.message) || "Error del portal");
        var noEpisodes = /100006|100008|no existe|不存在/i.test(message);
        if (!retried && !noEpisodes) {
          $("#detail-episodes").innerHTML = '<div class="episode-row"><div class="episode-info"><div class="episode-title">Reintentando capítulos…</div><div class="episode-overview">La conexión con el portal tardó. Un momento.</div></div></div>';
          setTimeout(function () { loadEpisodes(target, true); }, 700);
          return [];
        }
        $("#detail-episodes").innerHTML =
          '<div class="episode-row"><div class="episode-info">' +
          '<div class="episode-title">' + (noEpisodes ? "Este título no tiene capítulos disponibles" : "No se pudieron cargar los capítulos") + "</div>" +
          '<div class="episode-overview">' + escapeHtml(noEpisodes ? "Puedes reproducir el título directamente." : message) + "</div>" +
          "</div></div>" +
          (noEpisodes ? "" : '<button class="secondary-btn episode-retry" data-focusable>Reintentar</button>');
        var retry = $(".episode-retry", $("#detail-episodes"));
        if (retry) {
          retry.addEventListener("click", function () {
            loadEpisodes(target);
          });
        }
        return [];
      });
  }

  /**
   * El portal rotula sus capítulos con marcas técnicas («Serie Season1 - 1»,
   * «Serie T1_11»). Se limpian para mostrar el nombre propio o «Capítulo N».
   */
  function cleanEpisodeTitle(raw, number, seriesTitle) {
    var text = String(raw || "")
      .replace(/_/g, " ")
      .replace(/\s+/g, " ")
      .trim();
    if (!text) return "";
    text = text
      .replace(/\b(?:season|temporada)\s*\d{1,2}\s*[-–—·:]?\s*\d{1,3}\b/ig, " ")
      .replace(/\b[Tt]\d{1,2}\s*[-–—·_:]?\s*\d{1,3}\b/g, " ")
      .replace(/\bS\d{1,2}\s*[-–—·_:]?\s*E\d{1,3}\b/ig, " ")
      .replace(/\b(?:cap[ií]tulo|episodio|episode)\s*\d{1,3}\b/ig, " ")
      .replace(/\s+/g, " ")
      .trim()
      .replace(/^[\s\-–—·:]+|[\s\-–—·:]+$/g, "")
      .trim();
    if (!text) return "";
    if (/^\d{1,3}$/.test(text)) return "";
    var series = String(seriesTitle || "").replace(/_/g, " ").replace(/\s+/g, " ").trim();
    if (series && text.toLowerCase() === series.toLowerCase()) return "";
    return text;
  }

  function episodeLabel(episode, seriesTitle) {
    var number = (episode && episode.number) || 1;
    var clean = cleanEpisodeTitle(episode && (episode.title || episode.name), number, seriesTitle);
    return clean || "Capítulo " + number;
  }

  function renderEpisodes() {
    var holder = $("#detail-episodes");
    holder.innerHTML = "";
    var episodes = state.detail.episodes || [];
    if (!episodes.length) {
      holder.innerHTML = '<div class="episode-row"><div class="episode-info"><div class="episode-title">Sin capítulos disponibles</div><div class="episode-overview">El título no tiene capítulos en el índice ni en el portal.</div></div></div>';
      return;
    }
    var season = state.detail.seasons[state.detail.seasonIndex] || null;
    episodes.forEach(function (episode) {
      var row = el("button", "episode-row");
      row.setAttribute("data-focusable", "true");
      var still = episode.still || posterOf(state.detail.film);
      row.innerHTML =
        (episode.still ? '<img class="episode-thumb" loading="lazy" src="' + escapeHtml(still) + '" alt="">' : "") +
        '<div class="episode-number">' + escapeHtml(episode.number || "") + "</div>" +
        '<div class="episode-info">' +
        '<div class="episode-title">' + escapeHtml(episodeLabel(episode, state.detail.film && state.detail.film.title)) + "</div>" +
        (episode.overview ? '<div class="episode-overview">' + escapeHtml(episode.overview) + "</div>" : "") +
        "</div>";
      row.addEventListener("click", function () {
        playFilm(state.detail.film, episode, season || episode.season || null);
      });
      holder.appendChild(row);
    });
  }

  /* ============================ reproducción ============================ */

  function resolveParams(film, episode, season, forceRelay) {
    var params = {
      forceRelay: Boolean(forceRelay),
      playbackMode: forceRelay ? "smooth" : "normal",
      quality: state.settings.quality,
      trackIndex: state.player.trackIndex || 0,
      audioTrack: state.player.audioIndex || 0,
    };
    if (episode && episode.contentId) {
      params.contentId = episode.contentId;
      params.seriesContentId = String((season && season.titleId) || (film && film.id) || "")
        .replace(/^xtv:/, "")
        .replace(/^magis(?:vod)?:/, "");
      params.ref = "xtv:" + episode.contentId;
    } else {
      params.ref = (film && (film.videoUrl || film.id)) || "";
      params.contentId = contentIdOf(film);
    }
    return params;
  }

  function playFilm(film, episode, season) {
    if (!film) return;
    if (isLive(film)) {
      playLive(film);
      return;
    }
    if (isSeries(film) && !episode) {
      openDetail(film);
      return;
    }
    openPlayer(film, episode || null, season || null, false);
  }

  function playLive(film) {
    debugLog("playLive " + (film && film.title));
    openPlayer(film, null, null, true);
  }

  function openPlayer(film, episode, season, isLiveMode) {
    var video = $("#player-video");
    debugLog("openPlayer " + (film && film.title) + " screen=" + state.screen);
    if (!video) return;
    state.player.film = film;
    state.player.episode = episode || null;
    state.player.season = season || null;
    state.player.isLive = Boolean(isLiveMode);
    state.player.duration = 0;
    state.player.subtitles = [];
    state.player.subtitleTracks = [];
    state.player.subtitleCues = [];
    state.player.subtitleIndex = -1;
    state.player.subtitleEnabled = state.settings.subs !== "off";
    state.player.availableTracks = [];
    state.player.trackIndex = 0;
    state.player.audioTracks = [];
    state.player.audioIndex = 0;
    state.player.markers = null;
    state.player.introSkipped = false;
    state.player.pendingSeek = 0;
    state.player.relayBase = "";
    state.player.relayOffset = 0;
    state.player.externalUrl = (film && film.externalUrl) || "";
    state.player.headers = (film && film.headers) || {};
    state.player.sport = (film && film.sport) || null;
    state.player.retrying = false;
    state.player.retryHint = false;
    state.player.scrubRatio = null;
    state.player.lastSave = 0;
    if (state.screen !== "player") state.player.returnScreen = state.screen;
    state.player.forceRelay = RELAY_TEST || state.settings.render === "smooth";
    $("#osd-audio").hidden = true;
    $("#osd-quality").hidden = true;
    $("#osd-subs-label").textContent = "Sin subtítulos";

    var episodeTitle = episode
      ? cleanEpisodeTitle(episode.title || episode.name, episode.number, film && film.title)
      : "";
    $("#player-title").textContent = (film && film.title) || "";
    $("#player-subtitle").textContent = episode
      ? "T" + ((season && season.n) || 1) + " E" + (episode.number || "") + (episodeTitle ? " · " + episodeTitle : "")
      : isLiveMode
        ? "Señal en vivo"
        : kindLabel(film);
    $("#player-badge").hidden = !isLiveMode;
    $("#player-badge").textContent = isLiveMode ? "EN VIVO" : "";
    $("#match-hud").hidden = true;
    $("#osd-stats").hidden = !state.player.sport;
    if (!state.player.sport) closeMatchCenter(false);
    if (state.player.sport) {
      if (state.player.sport.loading === undefined) state.player.sport.loading = !state.player.sport.stats;
      loadSportStats(state.player.sport).then(function () {
        if (state.screen === "player" && state.player.sport) renderMatchHud();
        if (state.matchCenter.info === state.player.sport && !$("#match-center-dialog").hidden) renderMatchCenter();
      });
    }
    $(".osd-progress-wrap", $("#player-osd")).hidden = isLiveMode;
    $("#osd-rew").hidden = isLiveMode;
    $("#osd-ffw").hidden = isLiveMode;
    $("#player-loading").hidden = false;
    $("#player-loading-text").textContent = isLiveMode ? "Conectando con la señal" : "Preparando la reproducción";
    $("#player-message").classList.remove("show");
    $("#player-message").textContent = "";
    $("#subtitle-overlay").textContent = "";
    video.pause();
    video.removeAttribute("src");
    video.load();
    updateOsd();
    showScreen("player", { noStack: true });
    showOsd();
    if (state.player.externalUrl) startExternalPlayback();
    else startPlayback(film, episode, season, isLiveMode, state.player.forceRelay);
  }

  function startPlayback(film, episode, season, isLiveMode, forceRelay) {
    var video = $("#player-video");
    var method = isLiveMode ? "resolveLive" : "resolve";
    var params = isLiveMode
      ? {
          channelCode: String((film && film.videoUrl) || "").replace(/^xtvlive:/, ""),
          forceRelay: Boolean(forceRelay),
          headers: state.player.headers || {},
        }
      : resolveParams(film, episode, season, forceRelay);
    if (isLiveMode && /^https?:\/\//i.test((film && film.videoUrl) || "")) {
      params.streamUrl = film.videoUrl;
    }
    var timeout = isLiveMode ? 45000 : 60000;
    request(method, params, timeout)
      .then(function (stream) {
        if (!stream || stream.returnValue === false) throw new Error((stream && stream.errorText) || "El servicio no devolvió el stream");
        if (!stream.proxyPath) throw new Error("El servicio no devolvió una ruta reproducible");
        state.player.isLive = Boolean(isLiveMode || stream.isLive);
        if (stream.isRelay !== undefined) state.player.forceRelay = Boolean(stream.isRelay);
        state.player.relayBase = stream.isRelay ? String(stream.proxyPath || "").split("?")[0] : "";
        state.player.relayOffset = 0;
        state.player.duration = Number(stream.duration) || (film && film.durationMin ? film.durationMin * 60 : 0);
        state.player.availableTracks = stream.availableTracks || [];
        state.player.trackIndex = Number(stream.trackIndex) || 0;
        state.player.audioTracks = stream.audioTracks || [];
        if (state.player.audioTracks.length) {
          if (state.player.forceRelay && !state.player.audioIndex) {
            var spanish = state.player.audioTracks.indexOf("spa");
            if (spanish < 0) spanish = state.player.audioTracks.indexOf("es");
            if (spanish >= 0) state.player.audioIndex = spanish;
          }
          if (!state.player.forceRelay) state.player.audioIndex = 0;
        }
        state.player.subtitles = stream.subtitles || [];
        state.player.subtitleTracks = stream.subtitles || [];
        $("#player-loading-text").textContent = state.player.isLive ? "Conectando con la señal" : "Preparando la reproducción";
        var initialSeek = state.player.isLive ? 0 : Math.max(0, Math.floor(Number(state.player.pendingSeek) || 0));
        if (state.player.relayBase) {
          // El relay FFmpeg no es seekable por Range: se abre en el minuto
          // pedido y se toma ese offset como base del tiempo mostrado.
          state.player.relayOffset = initialSeek;
          state.player.pendingSeek = 0;
          video.src = relayUrlAt(initialSeek);
        } else {
          video.src = stream.proxyPath;
        }
        video.load();
        var playPromise = video.play();
        if (playPromise && playPromise.catch) {
          playPromise.catch(function () {
            showPlayerMessage("Pulsa OK para iniciar la reproducción.");
            $("#player-loading").hidden = true;
          });
        }
        loadSubtitles(state.player.subtitles);
        applyPlayerOptions();
        loadMarkers();
        updateOsd();
        setPlayerLoadingTimeout(18000);
      })
      .catch(function (error) {
        $("#player-loading").hidden = true;
        var canRelay = Boolean(state.serviceInfo && state.serviceInfo.relayAvailable);
        showPlayerMessage(
          "No se pudo iniciar: " +
            error.message +
            (!forceRelay && canRelay ? " · Pulsa OK para reintentar en modo suave." : ""),
        );
        state.player.retryHint = !forceRelay && canRelay;
      });
  }

  function startExternalPlayback() {
    var video = $("#player-video");
    var url = state.player.externalUrl;
    var mime = /\.mpd(\?|$)/i.test(url)
      ? "application/dash+xml"
      : /\.m3u8(\?|$)/i.test(url)
        ? "application/x-mpegurl"
        : "";
    request("resolve", {
      ref: url,
      headers: state.player.headers || {},
      forceRelay: RELAY_TEST,
      isLive: true,
      mime: mime,
      drmKey: state.player.film && state.player.film.drmKey ? state.player.film.drmKey : "",
    }, 60000)
      .then(function (stream) {
        if (!stream || !stream.proxyPath) throw new Error((stream && stream.errorText) || "El servicio no devolvió la señal");
        state.player.isLive = true;
        state.player.duration = 0;
        state.player.availableTracks = [];
        state.player.audioTracks = [];
        video.src = stream.proxyPath;
        video.load();
        var playPromise = video.play();
        if (playPromise && playPromise.catch) {
          playPromise.catch(function () {
            showPlayerMessage("Pulsa OK para iniciar la reproducción.");
            $("#player-loading").hidden = true;
          });
        }
        applyPlayerOptions();
        updateOsd();
        setPlayerLoadingTimeout(18000);
      })
      .catch(function (error) {
        $("#player-loading").hidden = true;
        showPlayerMessage("No se pudo abrir la señal: " + error.message);
      });
  }

  function languageLabel(code) {
    var value = String(code || "").toLowerCase();
    if (/^(spa|es|esp)/.test(value)) return "Español";
    if (/^(eng|en)/.test(value)) return "Inglés";
    if (/^(por|pt)/.test(value)) return "Portugués";
    return value ? value.toUpperCase() : "Audio";
  }

  function applyPlayerOptions() {
    var player = state.player;
    var tracks = player.availableTracks || [];
    var audio = player.audioTracks || [];
    if (!player.isLive && tracks.length > 1) {
      $("#osd-quality").hidden = false;
      var current = tracks[player.trackIndex] || tracks[0] || {};
      $("#osd-quality-label").textContent = "Calidad: " + (current.quality || current.height || "Auto");
    } else {
      $("#osd-quality").hidden = true;
    }
    if (!player.isLive && audio.length >= 1 && state.serviceInfo && state.serviceInfo.relayAvailable) {
      $("#osd-audio").hidden = false;
      $("#osd-audio-label").textContent = languageLabel(audio[player.audioIndex] || audio[0]);
    } else {
      $("#osd-audio").hidden = true;
    }
    if (player.subtitleTracks.length) {
      $("#osd-subs-label").textContent = "Subtítulos";
    } else {
      $("#osd-subs-label").textContent = "Sin subtítulos";
    }
  }

  function reloadStream(options) {
    var opts = options || {};
    var video = $("#player-video");
    if (opts.trackIndex !== undefined) state.player.trackIndex = opts.trackIndex;
    if (opts.audioIndex !== undefined) state.player.audioIndex = opts.audioIndex;
    if (opts.forceRelay !== undefined) state.player.forceRelay = Boolean(opts.forceRelay);
    state.player.pendingSeek = Number(opts.seekTo) || 0;
    state.player.retrying = false;
    state.player.retryHint = false;
    $("#player-loading").hidden = false;
    $("#player-loading-text").textContent = "Cambiando opciones de reproducción";
    $("#player-message").classList.remove("show");
    video.pause();
    video.removeAttribute("src");
    video.load();
    startPlayback(
      state.player.film,
      state.player.episode,
      state.player.season,
      state.player.isLive,
      state.player.forceRelay,
    );
  }

  function cycleQuality() {
    var tracks = state.player.availableTracks || [];
    if (state.player.isLive || tracks.length < 2) {
      toast("Este título no tiene más calidades disponibles.");
      return;
    }
    var next = (state.player.trackIndex + 1) % tracks.length;
    var seek = playerPosition();
    reloadStream({ trackIndex: next, seekTo: seek, forceRelay: state.player.forceRelay });
    var track = tracks[next] || {};
    toast("Calidad: " + (track.quality || track.height || "Auto") + (track.codec ? " · " + track.codec : ""));
  }

  function cycleAudio() {
    var audio = state.player.audioTracks || [];
    if (audio.length < 2) {
      toast("Este título no tiene más pistas de audio.");
      return;
    }
    if (!state.serviceInfo || !state.serviceInfo.relayAvailable) {
      toast("El cambio de audio requiere el modo ffmpeg (no disponible en este equipo).");
      return;
    }
    var next = (state.player.audioIndex + 1) % audio.length;
    var seek = playerPosition();
    reloadStream({ audioIndex: next, seekTo: seek, forceRelay: true });
    toast("Audio: " + audio[next]);
  }

  function loadMarkers() {
    var player = state.player;
    player.markers = null;
    player.introSkipped = false;
    if (!player.episode || player.isLive) return;
    var ref = (player.season && player.season.titleId) || (player.film && player.film.id) || "";
    if (!/^xtv:/.test(ref)) return;
    request(
      "markers",
      {
        ref: ref,
        ep: player.episode.number || 1,
        len: Math.round(player.duration || 0),
      },
      20000,
    )
      .then(function (payload) {
        if (state.player.film === player.film && state.player.episode === player.episode) {
          state.player.markers = payload || null;
        }
      })
      .catch(function () {
        state.player.markers = null;
      });
  }

  function maybeSkipIntro(time) {
    var player = state.player;
    if (state.settings.skipIntro === "off" || player.isLive || !player.markers || !player.markers.intro) return;
    if (player.introSkipped) return;
    var start = Number(player.markers.intro.s || 0) / 1000;
    var end = Number(player.markers.intro.e || 0) / 1000;
    if (!start || !end) return;
    if (time >= start && time < end) {
      if (isRelayPlayback()) {
        seekToPosition(end + 1);
      } else {
        var video = $("#player-video");
        try {
          video.currentTime = end + 1;
        } catch (error) {
          /* noop */
        }
      }
      player.introSkipped = true;
      toast("Intro omitido");
    } else if (time >= end) {
      player.introSkipped = true;
    }
  }

  function setPlayerLoadingTimeout(ms) {
    clearTimeout(state.player.loadingTimer);
    state.player.loadingTimer = setTimeout(function () {
      if ($("#player-loading").hidden) return;
      if (!state.player.isLive && !state.player.retrying && state.serviceInfo && state.serviceInfo.relayAvailable) {
        retrySmooth();
      } else {
        $("#player-loading").hidden = true;
        showPlayerMessage("La señal está tardando. Pulsa OK para reintentar.");
        state.player.retryHint = true;
      }
    }, ms);
  }

  function retrySmooth() {
    if (state.player.retrying || !state.player.film) return;
    state.player.retrying = true;
    state.player.retryHint = false;
    state.player.pendingSeek = state.player.isLive ? 0 : playerPosition();
    $("#player-loading").hidden = false;
    $("#player-loading-text").textContent = "Reintentando en modo suave (ffmpeg)";
    $("#player-message").classList.remove("show");
    var video = $("#player-video");
    video.pause();
    video.removeAttribute("src");
    video.load();
    startPlayback(state.player.film, state.player.episode, state.player.season, state.player.isLive, true);
  }

  function closePlayer() {
    debugLog("closePlayer screen=" + state.screen);
    saveProgress(true);
    var video = $("#player-video");
    if (video) {
      try {
        video.pause();
        video.removeAttribute("src");
        video.load();
      } catch (error) {
        /* noop */
      }
    }
    clearTimeout(state.player.loadingTimer);
    clearTimeout(state.player.osdTimer);
    $("#subtitle-overlay").textContent = "";
    $("#player-message").classList.remove("show");
    var returnScreen = state.player.returnScreen || "home";
    state.player.film = null;
    state.player.episode = null;
    state.player.season = null;
    state.player.isLive = false;
    state.player.sport = null;
    state.player.relayBase = "";
    state.player.relayOffset = 0;
    $("#match-hud").hidden = true;
    closeMatchCenter(false);
    if (returnScreen === "player") returnScreen = "home";
    showScreen(returnScreen, { noStack: true });
    if (returnScreen === "detail") {
      setTimeout(function () {
        Focus.first("#detail-play");
      }, 60);
    } else if (returnScreen === "live") {
      setTimeout(function () {
        Focus.first(".vt-channel-card");
      }, 60);
    } else if (returnScreen === "sports") {
      setTimeout(function () {
        Focus.first("#sports-content .vt-match-card");
      }, 60);
    } else {
      setTimeout(function () {
        Focus.first("#home-hero .primary-btn");
      }, 60);
    }
  }

  function showPlayerMessage(message) {
    var node = $("#player-message");
    node.textContent = message;
    node.classList.add("show");
    clearTimeout(showPlayerMessage.timer);
    showPlayerMessage.timer = setTimeout(function () {
      node.classList.remove("show");
    }, 7000);
  }

  function isRelayPlayback() {
    return Boolean(state.player.relayBase);
  }

  function relayUrlAt(offset) {
    var base = state.player.relayBase;
    if (!base) return "";
    var parts = [];
    if (Number(offset) > 0.5) parts.push("start=" + Math.floor(Number(offset)));
    if (Number(state.player.audioIndex) > 0) parts.push("a=" + Number(state.player.audioIndex));
    return base + (parts.length ? "?" + parts.join("&") : "");
  }

  function playerPosition() {
    var video = $("#player-video");
    var raw = video ? Number(video.currentTime) || 0 : 0;
    return raw + (isRelayPlayback() ? Number(state.player.relayOffset) || 0 : 0);
  }

  function playerDuration() {
    var player = state.player;
    var declared = Number(player.duration) || 0;
    var video = $("#player-video");
    var measured = video ? Number(video.duration) : 0;
    if (isRelayPlayback()) return declared || (measured > 60 && isFinite(measured) ? measured : 0);
    return measured && isFinite(measured) ? measured : declared;
  }

  function seekToPosition(seconds) {
    var video = $("#player-video");
    var player = state.player;
    if (!video || player.isLive) return;
    var duration = playerDuration();
    var target = Number(seconds) || 0;
    if (target < 0) target = 0;
    if (duration > 0 && target > duration - 2) target = Math.max(0, duration - 2);
    if (isRelayPlayback()) {
      var wasPlaying = !video.paused;
      target = Math.floor(target);
      player.relayOffset = target;
      video.src = relayUrlAt(target);
      video.load();
      if (wasPlaying) {
        var promise = video.play();
        if (promise && promise.catch) promise.catch(function () {});
      }
      showOsd();
      updateOsd();
      return;
    }
    try {
      video.currentTime = target;
    } catch (error) {
      /* noop */
    }
    showOsd();
    updateOsd();
  }

  function togglePictureInPicture() {
    var video = $("#player-video");
    if (!video) return;
    try {
      if (document.pictureInPictureElement) {
        var leave = document.exitPictureInPicture();
        if (leave && leave.catch) leave.catch(function () {});
      } else if (document.pictureInPictureEnabled && video.readyState >= 1) {
        var enter = video.requestPictureInPicture();
        if (enter && enter.catch) enter.catch(function () {});
      }
    } catch (error) {
      /* noop */
    }
  }

  function saveProgress(force) {
    var player = state.player;
    if (!player.film || player.isLive) return;
    var video = $("#player-video");
    if (!video) return;
    var position = playerPosition();
    var duration = playerDuration() || (player.film && player.film.durationMin ? player.film.durationMin * 60 : 0);
    if (duration <= 0 || position < 10) return;
    var now = Date.now();
    if (!force && now - player.lastSave < 5000) return;
    player.lastSave = now;
    var key = progressKey(player.film, player.episode);
    if (!key) return;
    var finished = position >= duration - 15;
    if (finished) {
      delete state.progress[key];
    } else {
      state.progress[key] = {
        position: position,
        duration: duration,
        updatedAt: now,
        film: player.film,
        episode: player.episode,
        season: player.season,
      };
    }
    writeJson("vp_webos_progress", state.progress);
  }

  function updateOsd() {
    var video = $("#player-video");
    var player = state.player;
    var duration = playerDuration();
    var current = playerPosition();
    if (player.scrubRatio !== null && player.scrubRatio !== undefined && duration > 0) {
      current = clamp(Number(player.scrubRatio) || 0, 0, 1) * duration;
    }
    $("#osd-current").textContent = player.isLive ? "EN VIVO" : formatTime(current);
    $("#osd-total").textContent = player.isLive ? "" : duration ? formatTime(duration) : "--:--";
    var pct = duration > 0 ? clamp((current / duration) * 100, 0, 100) : 0;
    $("#osd-played").style.width = pct + "%";
    $("#osd-thumb").style.left = pct + "%";
    var buffered = video.buffered;
    if (buffered && buffered.length && duration > 0) {
      var bufferedEnd = buffered.end(buffered.length - 1);
      if (isRelayPlayback()) bufferedEnd += Number(state.player.relayOffset) || 0;
      $("#osd-buffered").style.width = clamp((bufferedEnd / duration) * 100, 0, 100) + "%";
    }
    var playing = !video.paused && !video.ended;
    $("#osd-toggle-icon").innerHTML = '<use href="#' + (playing ? "i-pause" : "i-play") + '"/>';
    $("#osd-toggle-label").textContent = playing ? "Pausa" : "Reproducir";
  }

  function showOsd() {
    var screen = $("#screen-player");
    screen.classList.add("show-osd");
    if (!Focus.current || !Focus.current.closest("#screen-player")) {
      Focus.set($("#osd-toggle"));
    }
    clearTimeout(state.player.osdTimer);
    state.player.osdTimer = setTimeout(function () {
      if (!$("#screen-player").classList.contains("show-osd")) return;
      hideOsd();
    }, 6500);
    updateOsd();
    renderMatchHud();
  }

  function hideOsd() {
    $("#screen-player").classList.remove("show-osd");
    $("#match-hud").hidden = true;
    clearTimeout(state.player.osdTimer);
    if (Focus.current && Focus.current.closest("#screen-player")) Focus.clear();
  }

  function togglePlay() {
    var video = $("#player-video");
    if (!video) return;
    if (video.paused) {
      var promise = video.play();
      if (promise && promise.catch) promise.catch(function () {});
    } else {
      video.pause();
    }
    showOsd();
  }

  function toggleImageMode() {
    var video = $("#player-video");
    if (!video) return;
    var fill = video.classList.toggle("fill-screen");
    video.style.objectFit = fill ? "cover" : "contain";
    var label = $("#osd-image-label");
    if (label) label.textContent = fill ? "Imagen: Rellenar" : "Imagen: Original";
  }

  function seekBy(delta) {
    if (state.player.isLive) return;
    seekToPosition(playerPosition() + Number(delta || 0));
  }

  /* ============================ subtítulos ============================ */

  function langMatches(lang, want) {
    var value = String(lang || "").toLowerCase();
    var target = String(want || "").toLowerCase();
    if (!target || target === "off") return false;
    if (target === "es") return /^(es|spa|esp)/.test(value);
    if (target === "en") return /^(en|eng)/.test(value);
    return value.indexOf(target) === 0;
  }

  function loadSubtitles(subtitles) {
    state.player.subtitleTracks = subtitles || [];
    state.player.subtitleCues = [];
    state.player.subtitleIndex = -1;
    $("#subtitle-overlay").textContent = "";
    if (!state.player.subtitleTracks.length) {
      state.player.subtitleEnabled = false;
      applyPlayerOptions();
      return;
    }
    if (state.settings.subs === "off") {
      state.player.subtitleEnabled = false;
      applyPlayerOptions();
      return;
    }
    var tracks = state.player.subtitleTracks;
    var index = tracks.findIndex(function (sub) {
      return langMatches(sub.lang, state.settings.subs);
    });
    if (index < 0) {
      index = tracks.findIndex(function (sub) {
        return langMatches(sub.lang, "es");
      });
    }
    if (index < 0) index = 0;
    state.player.subtitleEnabled = true;
    selectSubtitle(index);
  }

  function selectSubtitle(index) {
    var tracks = state.player.subtitleTracks || [];
    var track = tracks[index];
    state.player.subtitleIndex = index;
    state.player.subtitleCues = [];
    $("#subtitle-overlay").textContent = "";
    if (!track) {
      state.player.subtitleEnabled = false;
      applyPlayerOptions();
      return;
    }
    state.player.subtitleEnabled = true;
    applyPlayerOptions();
    fetch(track.proxyUrl || track.url)
      .then(function (response) {
        return response.text();
      })
      .then(function (text) {
        if (state.player.subtitleIndex !== index || !state.player.subtitleEnabled) return;
        state.player.subtitleCues = parseSrt(text);
        applyPlayerOptions();
      })
      .catch(function () {
        if (state.player.subtitleIndex === index) {
          state.player.subtitleCues = [];
          applyPlayerOptions();
        }
      });
  }

  function parseSrt(raw) {
    var cues = [];
    var blocks = String(raw || "").replace(/\r\n/g, "\n").replace(/\r/g, "\n").split(/\n\n+/);
    blocks.forEach(function (block) {
      var lines = block.split("\n").map(function (line) {
        return line.trim();
      }).filter(Boolean);
      if (!lines.length) return;
      var timeIndex = lines.findIndex(function (line) {
        return line.indexOf("-->") >= 0;
      });
      if (timeIndex < 0) return;
      var times = lines[timeIndex].split("-->");
      var start = parseSrtTime(times[0]);
      var end = parseSrtTime(times[1]);
      var text = lines.slice(timeIndex + 1).join("\n").replace(/<[^>]+>/g, "").trim();
      if (text && end > start) cues.push({ start: start, end: end, text: text });
    });
    return cues;
  }

  function parseSrtTime(value) {
    var match = String(value || "").trim().match(/(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})/);
    if (!match) return 0;
    return Number(match[1]) * 3600 + Number(match[2]) * 60 + Number(match[3]) + Number(match[4]) / 1000;
  }

  function updateSubtitle(time) {
    var overlay = $("#subtitle-overlay");
    if (!state.player.subtitleEnabled || !state.player.subtitleCues.length) {
      if (overlay.textContent) overlay.textContent = "";
      return;
    }
    var cue = null;
    for (var i = 0; i < state.player.subtitleCues.length; i += 1) {
      if (time >= state.player.subtitleCues[i].start && time <= state.player.subtitleCues[i].end) {
        cue = state.player.subtitleCues[i];
        break;
      }
    }
    if (cue) {
      var text = escapeHtml(cue.text).replace(/\n/g, "<br>");
      if (overlay.innerHTML !== text) overlay.innerHTML = text;
    } else if (overlay.textContent) {
      overlay.textContent = "";
    }
  }

  function cycleSubtitles() {
    var tracks = state.player.subtitleTracks || [];
    if (!tracks.length) {
      toast("Este título no trae subtítulos.");
      return;
    }
    var next;
    if (!state.player.subtitleEnabled || state.player.subtitleIndex < 0) next = 0;
    else next = state.player.subtitleIndex + 1;
    if (next >= tracks.length) {
      state.player.subtitleEnabled = false;
      state.player.subtitleIndex = -1;
      state.player.subtitleCues = [];
      state.settings.subs = "off";
      $("#subtitle-overlay").textContent = "";
      toast("Subtítulos desactivados");
    } else {
      selectSubtitle(next);
      state.settings.subs = tracks[next].lang || "es";
      toast("Subtítulos: " + (tracks[next].lang || "on"));
    }
    writeJson("vp_webos_settings", state.settings);
    updateSettingsUi();
    applyPlayerOptions();
    updateOsd();
    showOsd();
  }

  /* ============================ reproductor: teclas ============================ */

  function handlePlayerKey(code, event) {
    var osdVisible = $("#screen-player").classList.contains("show-osd");
    var matchDialog = $("#match-center-dialog");
    if (matchDialog && !matchDialog.hidden) {
      if (code === 461 || code === 27) {
        event.preventDefault();
        closeMatchCenter();
        return true;
      }
      if (code === 37 || code === 38 || code === 39 || code === 40) {
        event.preventDefault();
        Focus.move(code === 37 ? "left" : code === 39 ? "right" : code === 38 ? "up" : "down");
        return true;
      }
      if (code === 13) {
        event.preventDefault();
        Focus.activate();
        return true;
      }
      return false;
    }
    if (osdVisible && Focus.current && Focus.current.closest && Focus.current.closest("#match-hud")) {
      if (code === 37 || code === 38 || code === 39 || code === 40) {
        event.preventDefault();
        Focus.move(code === 37 ? "left" : code === 39 ? "right" : code === 38 ? "up" : "down");
        return true;
      }
      if (code === 13) {
        event.preventDefault();
        Focus.activate();
        return true;
      }
    }
    if (PC && code === 80) {
      event.preventDefault();
      togglePictureInPicture();
      return true;
    }
    if (code === 32) {
      event.preventDefault();
      togglePlay();
      return true;
    }
    if (code === 461 || code === 27) {
      event.preventDefault();
      if (osdVisible) {
        hideOsd();
      } else {
        closePlayer();
      }
      return true;
    }
    if (code === 13) {
      event.preventDefault();
      var message = $("#player-message");
      if (message.classList.contains("show") && state.player.retryHint) {
        state.player.retryHint = false;
        message.classList.remove("show");
        if (state.player.isLive) {
          state.player.retrying = false;
          $("#player-loading").hidden = false;
          $("#player-loading-text").textContent = "Reconectando con la señal";
          startPlayback(
            state.player.film,
            state.player.episode,
            state.player.season,
            true,
            Boolean(state.serviceInfo && state.serviceInfo.relayAvailable),
          );
        } else {
          retrySmooth();
        }
        return true;
      }
      if (!osdVisible) {
        showOsd();
        return true;
      }
      if (Focus.current && Focus.current.closest("#screen-player")) {
        Focus.activate();
      } else {
        togglePlay();
      }
      return true;
    }
    if (code === 37 || code === 38 || code === 39 || code === 40) {
      event.preventDefault();
      if (!osdVisible) {
        showOsd();
        return true;
      }
      if (code === 37 || code === 39) {
        if (Focus.current && Focus.current.closest(".player-osd")) {
          Focus.move(code === 37 ? "left" : "right");
        } else {
          seekBy(code === 37 ? -10 : 10);
        }
      } else if (code === 38) {
        Focus.move("up");
      } else {
        hideOsd();
      }
      return true;
    }
    if (code === 415) {
      event.preventDefault();
      togglePlay();
      return true;
    }
    if (code === 19) {
      event.preventDefault();
      var video = $("#player-video");
      if (!video.paused) video.pause();
      showOsd();
      return true;
    }
    if (code === 417) {
      event.preventDefault();
      seekBy(-10);
      return true;
    }
    if (code === 418) {
      event.preventDefault();
      seekBy(10);
      return true;
    }
    if (code === 413) {
      event.preventDefault();
      closePlayer();
      return true;
    }
    return false;
  }

  /* ============================ búsqueda ============================ */

  function openSearch() {
    var searchScroll = $("#screen-search .search-results");
    if (searchScroll) searchScroll.scrollTop = 0;
    showScreen("search");
    renderKeyboard();
    renderSearchResults(state.search.items, false);
    updateSearchBox();
    setTimeout(function () {
      Focus.first(".key");
    }, 50);
  }

  function renderKeyboard() {
    var holder = $("#search-keyboard");
    holder.innerHTML = "";
    var keys = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".split("");
    keys.forEach(function (key) {
      var button = el("button", "key");
      button.setAttribute("data-focusable", "true");
      button.textContent = key;
      button.addEventListener("click", function () {
        state.search.physical = false;
        searchKey(key);
      });
      holder.appendChild(button);
    });
    var space = el("button", "key wide3");
    space.setAttribute("data-focusable", "true");
    space.textContent = "ESPACIO";
    space.addEventListener("click", function () {
      searchKey(" ");
    });
    holder.appendChild(space);
    var back = el("button", "key wide3");
    back.setAttribute("data-focusable", "true");
    back.textContent = "BORRAR";
    back.addEventListener("click", function () {
      searchKey("back");
    });
    holder.appendChild(back);
    var clear = el("button", "key wide3 accent");
    clear.setAttribute("data-focusable", "true");
    clear.textContent = "LIMPIAR";
    clear.addEventListener("click", function () {
      searchKey("clear");
    });
    holder.appendChild(clear);
    var search = el("button", "key wide4 accent");
    search.setAttribute("data-focusable", "true");
    search.textContent = "BUSCAR";
    search.addEventListener("click", function () {
      searchKey("search");
    });
    holder.appendChild(search);
  }

  function updateSearchBox() {
    var box = $("#search-box");
    var query = $("#search-query");
    if (state.search.query) {
      query.textContent = state.search.query;
      box.classList.add("has-query");
    } else {
      query.textContent = "Escribe el nombre de una película, serie o canal";
      box.classList.remove("has-query");
    }
  }

  function searchKey(key) {
    if (key === "back") {
      state.search.query = state.search.query.slice(0, -1);
    } else if (key === "clear") {
      state.search.query = "";
    } else if (key === "search") {
      clearTimeout(state.search.timer);
      runSearch(true);
      return;
    } else {
      state.search.query += key;
    }
    updateSearchBox();
    if (!state.search.query.trim()) {
      state.search.generation = (state.search.generation || 0) + 1;
      renderSearchResults([], false);
      return;
    }
    clearTimeout(state.search.timer);
    state.search.timer = setTimeout(function () {
      runSearch(false);
    }, 380);
  }

  function runSearch(focusResults) {
    var query = state.search.query.trim();
    if (!query) {
      renderSearchResults([], false);
      return;
    }
    state.search.generation = (state.search.generation || 0) + 1;
    var generation = state.search.generation;
    $("#search-head").textContent = "Buscando…";
    request("search", { q: query, limit: 30 }, 25000)
      .then(function (data) {
        if (generation !== state.search.generation) return;
        state.search.items = data.films || [];
        renderSearchResults(state.search.items, focusResults);
        $("#search-head").textContent = "Resultados (" + state.search.items.length + ")";
      })
      .catch(function (error) {
        if (generation !== state.search.generation) return;
        $("#search-head").textContent = "No se pudo buscar: " + error.message;
      });
  }

  function renderSearchResults(items, focusResults) {
    var holder = $("#search-items");
    holder.innerHTML = "";
    var list = items && items.length ? items : state.popular;
    if (!list || !list.length) {
      $("#search-head").textContent = state.search.query ? "Sin resultados" : "Búsqueda popular";
      return;
    }
    if (!state.search.query) $("#search-head").textContent = "Búsqueda popular";
    uniqueFilms(list).forEach(function (film) {
      holder.appendChild(createGridCard(film, {
        onSelect: function (selected) {
          openDetail(selected);
        },
      }));
    });
    if (focusResults) {
      setTimeout(function () {
        Focus.first("#search-items .grid-card");
      }, 30);
    }
  }

  function clearSearch() {
    state.search.query = "";
    state.search.items = [];
    state.search.generation = (state.search.generation || 0) + 1;
    updateSearchBox();
    renderSearchResults([], false);
    setTimeout(function () {
      Focus.first(".key");
    }, 30);
  }

  /* ============================ grilla / catálogo ============================ */

  function openGrid(options) {
    var opts = options || {};
    state.grid = {
      title: opts.title || "Catálogo",
      items: opts.categoryId ? [] : (opts.films || []).slice(),
      page: 1,
      pageSize: 120,
      total: opts.total || (opts.films || []).length,
      categoryId: opts.categoryId || "",
      empty: opts.empty || "",
      loader: null,
    };
    $("#grid-title").textContent = state.grid.title;
    var gridScroll = $("#screen-grid .grid-wrap");
    if (gridScroll) gridScroll.scrollTop = 0;
    showScreen("grid");
    renderGrid(state.grid.items, false);
    $("#grid-more").hidden = true;
    if (state.grid.categoryId) loadGridPage(1, false);
    else {
      setTimeout(function () {
        Focus.first("#grid-items .grid-card") || Focus.first("#screen-grid .round-btn");
      }, 40);
    }
  }

  function openCatalog() {
    state.topTab = "";
    openGrid({ title: "Todo el catálogo", categoryId: "catalogo-completo" });
  }

  function loadGridPage(page, append) {
    if (!state.grid.categoryId) return Promise.resolve();
    $("#grid-more").hidden = true;
    return request("category", { id: state.grid.categoryId, page: page, pageSize: state.grid.pageSize }, 30000)
      .then(function (data) {
        var films = data.films || [];
        state.grid.total = Number(data.total) || state.grid.total;
        state.grid.page = page;
        if (append) state.grid.items = state.grid.items.concat(films);
        else state.grid.items = films;
        renderGrid(append ? films : state.grid.items, append);
        if (!append) {
          setTimeout(function () {
            Focus.first("#grid-items .grid-card");
          }, 40);
        }
        var loaded = state.grid.items.length;
        $("#grid-more").hidden = !(films.length >= state.grid.pageSize && loaded < state.grid.total);
        return films;
      })
      .catch(function (error) {
        toast("No se pudo cargar: " + error.message);
        return [];
      });
  }

  function renderGrid(items, append) {
    var holder = $("#grid-items");
    if (!append) holder.innerHTML = "";
    var startIndex = holder.children.length;
    uniqueFilms(items).forEach(function (film) {
      holder.appendChild(createGridCard(film));
    });
    if (!append && !holder.children.length && state.grid.empty) {
      holder.innerHTML =
        '<div class="grid-empty">' +
        '<div class="grid-empty-title">' + escapeHtml(state.grid.empty) + "</div>" +
        '<div class="grid-empty-help">Explora el catálogo o vuelve al Inicio.</div>' +
        "</div>";
    }
    if (append && holder.children.length > startIndex) {
      setTimeout(function () {
        Focus.set(holder.children[startIndex]);
      }, 30);
    }
  }

  /* ============================ TV en vivo ============================ */

  function liveNorm(value) {
    return String(value || "")
      .toUpperCase()
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/[^A-Z0-9]+/g, " ")
      .trim();
  }

  function liveCountry(name) {
    var n = liveNorm(name);
    var p = " " + n + " ";
    if (/(PUERTO RICO|EL SALVADOR|REPUBLICA DOMINICANA)/.test(n)) return "Internacional";
    if (p.indexOf(" CR ") >= 0 || /(COSTA RICA)/.test(n)) return "Costa Rica";
    if (p.indexOf(" MX ") >= 0 || /(MEXICO|MEXICANO|AZTECA|TELEVISA|LAS ESTRELLA|TL ESTRELLA|CANAL 5|TUDN|MULTIMEDIOS|IMAGEN|ECDF|TV AZTECA|ONCE|CANAL 22|CANAL 6|MILENIO|ADN40|EXCELSIOR|UNICABLE|BANDAMAX|DISTRITO COMEDIA|GOLDEN|BITME|TL NOVELA|DE PELICULA|TELEHIT|TELERITMO|A MAS|MEXICANAL|FORO|FOX ONE|AYM SPORTS|FOX SPORTS CENTRAL|FOX SPORTS 2 CENTRAL|FOX SPORTS 3 CENTRAL|FOX SPORTS PREMIUM)/.test(n)) return "México";
    if (p.indexOf(" US ") >= 0 || p.indexOf(" USA ") >= 0 || /(UNIVISION|UNIMAS|TELEMUNDO|GALAVISION|ESTRELLA TV|FOX DEPORTES|FOX SOCCER|ESPN DEPORTES|TNT SPORTS|CNN|NBC|CBS|ABC|FOX NEWS|FOX 5|FOX 40|BALLY|NESN|YES NETWORK)/.test(n)) return "Estados Unidos";
    if (p.indexOf(" AR ") >= 0 || /(ARGENTINA|TYC SPORTS|EL NUEVE|TELEFE|EL TRECE|AMERICA TV)/.test(n)) return "Argentina";
    if (p.indexOf(" CL ") >= 0 || /(CHILE|CANAL 13|MEGA|CHV|TVN|TNT SPORTS CHILE|TNT SPORTS PREMIUM|ESTADIO TNT SPORTS|TNT SPORTS FHD)/.test(n)) return "Chile";
    if (p.indexOf(" CO ") >= 0 || /(COLOMBIA|WIN SPORTS|CARACOL|RCN|CANAL 1)/.test(n)) return "Colombia";
    if (p.indexOf(" PE ") >= 0 || /(PERU|GOL PERU|LATINA|AMERICA TELEVISION)/.test(n)) return "Perú";
    if (p.indexOf(" ES ") >= 0 || /(ESPANA|SPAIN|MOVISTAR|LALIGA|MADRID)/.test(n)) return "España";
    return "Internacional";
  }

  var LIVE_POPULAR = [
    "MULTIMEDIOS", "IMAGEN", "CANAL 5", "LAS ESTRELLAS", "AZTECA", "TUDN",
    "FOX SPORTS", "ESPN", "CANAL 6", "MILENIO", "ADN40", "EL NUEVE",
    "CANAL 13", "CHILEVISION", "MEGA", "TVN", "CARACOL", "RCN", "WIN SPORTS",
    "AMERICA", "LATINA", "GOL PERU",
  ];

  function livePopularity(name) {
    var n = liveNorm(name);
    for (var i = 0; i < LIVE_POPULAR.length; i += 1) {
      if (n.indexOf(LIVE_POPULAR[i]) >= 0) return i;
    }
    return 999;
  }

  function liveCategory(name) {
    var n = liveNorm(name);
    if (/(ESPN|FOX SPORT|TUDN|DEPORTE|SPORT|FUTBOL|NBA|NFL|MLB|NHL|GOLF|TENNIS|MOTOGP|FORMULA|UFC|BOX|WRESTLING|TNT SPORTS|WIN SPORTS|DIRECTV SPORT|DAZN|BEIN|SKY SPORT|CLARO SPORT|CANAL 5|AZTECA 7|LAS ESTRELLAS|ECDF)/.test(n)) return "Deportes";
    if (/(24 7|24\/7)/.test(n)) return "24/7 Series";
    if (/(NOTICIA|NEWS|MILENIO|ADN40|EXCELSIOR|CNN|FORO TV|24 HORAS|IMAGEN)/.test(n)) return "Noticias";
    if (/(KIDS|INFANTIL|CARTOON|DISNEY|NICK|BABY|JUNIOR|CLAN|BOOMERANG)/.test(n)) return "Infantil";
    if (/(CINE|MOVIE|PELICULA|STARZ|SONY|HBO|STAR CHANNEL|CINEMA|FILM|GOLDEN|TNT NOVELAS)/.test(n)) return "Películas";
    if (/(SERIE|SERIES|NOVELA|TELENOVELA|COMEDY|DRAMA|ENTRETENIMIENTO)/.test(n)) return "Series";
    return "Entretenimiento";
  }

  function liveChannelMeta(film) {
    var code = channelCodeOf(film);
    var meta = state.live.catalog[code] || {};
    return {
      code: code,
      number: Number(meta.number) || 0,
      name: meta.name || film.title || "Canal",
      logo: meta.logo || posterOf(film),
      film: film,
      country: liveCountry(meta.name || film.title || ""),
      category: liveCategory(meta.name || film.title || ""),
    };
  }

  function openLive() {
    closeTopMenus();
    showScreen("live");
    state.live.page = 1;
    state.live.items = [];
    state.live.catalog = {};
    state.live.epg = {};
    $("#live-items").innerHTML = "";
    $("#live-count").textContent = "Cargando canales…";
    $("#live-more").hidden = true;
    $("#live-hero").innerHTML = '<div class="hero-loading"><div class="spinner"></div><span>Cargando TV en vivo</span></div>';
    loadLiveChannels();
  }

  function loadLiveChannels() {
    if (state.live.loading) return;
    state.live.loading = true;
    Promise.all([
      request("liveCatalog", {}, 60000).catch(function () { return { channels: [] }; }),
      // El API topa pageSize en 500; el catálogo de canales (liveCatalog)
      // sigue trayendo las 1,035 señales y rellena las que falten.
      request("live", { page: 1, pageSize: 500 }, 60000).catch(function () { return { films: [] }; }),
    ])
      .then(function (results) {
        var catalog = (results[0] && results[0].channels) || [];
        var films = (results[1] && results[1].films) || [];
        state.live.catalog = {};
        catalog.forEach(function (channel) {
          if (channel && channel.code) state.live.catalog[channel.code] = channel;
        });
        var byCode = {};
        films.forEach(function (film) {
          var code = channelCodeOf(film);
          if (code) byCode[code] = film;
        });
        catalog.forEach(function (channel) {
          if (!channel || !channel.code || byCode[channel.code]) return;
          byCode[channel.code] = {
            id: "xtv-live-" + channel.code,
            title: channel.name || channel.code,
            posterUrl: channel.logo || "",
            videoUrl: "xtvlive:" + channel.code,
            kind: "live",
            genres: [],
          };
        });
        state.live.items = Object.keys(byCode).map(function (code) { return byCode[code]; });
        state.live.total = state.live.items.length;
        state.live.loading = false;
        renderLiveFilters();
        filterLive(false);
        var epgMetas = state.live.items.map(liveChannelMeta).filter(liveMatchesFilters).sort(function (a, b) {
          var pa = livePopularity(a.name);
          var pb = livePopularity(b.name);
          if (pa !== pb) return pa - pb;
          return (a.number || 9999) - (b.number || 9999);
        });
        loadLiveEpg(epgMetas.slice(0, 220).map(function (meta) { return meta.film; }));
      })
      .catch(function (error) {
        state.live.loading = false;
        $("#live-count").textContent = "No se pudieron cargar los canales: " + error.message;
      });
  }

  function renderLiveFilters() {
    var categories = ["Todo", "Deportes", "24/7 Series", "Noticias", "Infantil", "Películas", "Series", "Favoritos"];
    var holder = $("#live-categories");
    if (!holder) return;
    holder.innerHTML = "";
    categories.forEach(function (category) {
      var button = el("button", "vt-cat-pill" + (state.live.category === category ? " is-active" : ""));
      button.setAttribute("data-focusable", "true");
      button.textContent = category;
      button.addEventListener("click", function () {
        state.live.category = category;
        filterLive();
      });
      holder.appendChild(button);
    });
    var label = $("#live-country-label");
    if (label) label.textContent = "País: " + state.live.country;
  }

  function liveCountries() {
    return ["México", "Todos", "Estados Unidos", "Argentina", "Chile", "Colombia", "Perú", "España", "Costa Rica", "Internacional"];
  }

  function openLiveCountryMenu() {
    var menu = $("#live-country-menu");
    if (!menu) return;
    menu.innerHTML = "";
    liveCountries().forEach(function (country) {
      var button = el("button", "vt-menu-item");
      button.setAttribute("data-focusable", "true");
      button.innerHTML = "<span><b>" + escapeHtml(country) + "</b></span>" + (country === state.live.country ? '<svg class="icon"><use href="#i-check"/></svg>' : "");
      button.addEventListener("click", function () {
        state.live.country = country;
        menu.hidden = true;
        renderLiveFilters();
        filterLive();
        toast("País: " + country);
      });
      menu.appendChild(button);
    });
    menu.hidden = false;
    setTimeout(function () {
      var selected = menu.querySelector(".vt-menu-item");
      if (selected) Focus.set(selected);
    }, 25);
  }

  function cycleLiveCountry() {
    var countries = liveCountries();
    var index = countries.indexOf(state.live.country);
    state.live.country = countries[(index + 1) % countries.length];
    renderLiveFilters();
    filterLive();
    toast("País: " + state.live.country);
  }

  function liveMatchesFilters(meta) {
    if (state.live.country !== "Todos" && meta.country !== state.live.country) return false;
    if (state.live.category === "Favoritos") return Boolean(state.live.favorites[meta.code]);
    if (state.live.category !== "Todo" && meta.category !== state.live.category) return false;
    return true;
  }

  function filterLive(focusFirst) {
    renderLiveFilters();
    var list = state.live.items.map(liveChannelMeta).filter(liveMatchesFilters);
    list.sort(function (a, b) {
      var pa = livePopularity(a.name);
      var pb = livePopularity(b.name);
      if (pa !== pb) return pa - pb;
      return (a.number || 9999) - (b.number || 9999);
    });
    if (!list.length) {
      $("#live-hero").innerHTML = '<div class="hero-loading"><span>No hay canales para este filtro.</span></div>';
      $("#live-items").innerHTML = "";
      $("#live-count").textContent = "0 canales";
      return;
    }
    renderLiveHero(list[0]);
    renderLive(list);
    $("#live-count").textContent = list.length + " canales";
    if (focusFirst !== false) {
      setTimeout(function () {
        var first = $("#live-items .vt-channel-card");
        if (first) Focus.set(first);
      }, 40);
    } else {
      setTimeout(function () {
        var first = $("#live-items .vt-channel-card");
        if (first) Focus.set(first);
      }, 40);
    }
  }

  function epgWindowText(program) {
    if (!program || !program.startMs || !program.endMs) return "";
    try {
      var fmt = new Intl.DateTimeFormat("es-MX", { hour: "2-digit", minute: "2-digit", hour12: false, timeZone: "America/Mexico_City" });
      return fmt.format(new Date(program.startMs)) + " - " + fmt.format(new Date(program.endMs));
    } catch (error) {
      return "";
    }
  }

  function renderLiveHero(meta) {
    var epg = state.live.epg[meta.code] || {};
    var now = epg.now || null;
    var next = epg.next || null;
    var percent = 0;
    var remaining = "";
    if (now && now.startMs && now.endMs) {
      var total = Math.max(1, now.endMs - now.startMs);
      var elapsed = Math.max(0, Date.now() - now.startMs);
      percent = clamp((elapsed / total) * 100, 0, 100);
      var mins = Math.max(0, Math.ceil((now.endMs - Date.now()) / 60000));
      remaining = mins > 0 ? "Quedan " + mins + " min" : "Terminando";
    }
    var logo = meta.logo ? '<img class="lh-logo" src="' + escapeHtml(meta.logo) + '" alt="">' : "";
    $("#live-hero").innerHTML =
      '<div class="lh-content">' +
      '<div class="lh-badge">EN VIVO</div>' +
      '<div class="lh-title">' + escapeHtml(meta.name) + "</div>" +
      '<div class="lh-program">' + escapeHtml((now && now.name) || "Señal en vivo") +
      (now ? '<span class="lh-window">' + escapeHtml(epgWindowText(now)) + " · " + escapeHtml(meta.category) + "</span>" : "") +
      "</div>" +
      (now ? '<div class="lh-bar"><span style="width:' + percent.toFixed(1) + '%"></span></div>' +
        '<div class="lh-remaining">' + escapeHtml(remaining) + "</div>" : "") +
      (next ? '<div class="lh-next">Sigue: ' + escapeHtml(next.name || "") + " (" + escapeHtml(epgWindowText(next).split(" - ")[0]) + ")</div>" : "") +
      '<div class="lh-hint">OK para ver a pantalla completa · Mantén OK para agregar a favoritos</div>' +
      "</div>" + logo;
  }

  function loadLiveEpg(films) {
    var channels = (films || []).map(function (film) {
      var meta = liveChannelMeta(film);
      return { code: meta.code, name: meta.name };
    }).filter(function (channel) { return channel.code || channel.name; });
    if (!channels.length) return;
    request("epgBulk", { channels: channels }, 25000)
      .then(function (data) {
        var map = (data && data.channels) || {};
        Object.keys(map).forEach(function (key) { state.live.epg[key] = map[key]; });
        var list = state.live.items.map(liveChannelMeta).filter(liveMatchesFilters);
        list.sort(function (a, b) {
          var pa = livePopularity(a.name);
          var pb = livePopularity(b.name);
          if (pa !== pb) return pa - pb;
          return (a.number || 9999) - (b.number || 9999);
        });
        if (list.length) renderLiveHero(list[0]);
        updateLiveEpg();
      })
      .catch(function () { /* la guía es opcional */ });
  }

  function updateLiveEpg() {
    $$("#live-items .vt-channel-card").forEach(function (card) {
      var code = card.getAttribute("data-channel-code") || "";
      var epg = state.live.epg[code];
      var program = $(".ch-program", card);
      if (!program) return;
      program.textContent = epg && epg.now && epg.now.name ? epg.now.name : "Señal en vivo";
      var progress = $(".ch-progress span", card);
      if (progress && epg && epg.now && epg.now.startMs && epg.now.endMs) {
        var total = Math.max(1, epg.now.endMs - epg.now.startMs);
        progress.style.width = clamp(((Date.now() - epg.now.startMs) / total) * 100, 0, 100).toFixed(1) + "%";
      }
    });
  }

  function renderLive(list) {
    var holder = $("#live-items");
    holder.innerHTML = "";
    list.forEach(function (meta) {
      var card = el("button", "vt-channel-card");
      card.setAttribute("data-focusable", "true");
      card.setAttribute("data-channel-code", meta.code);
      var logo = meta.logo
        ? '<img class="ch-logo" loading="lazy" src="' + escapeHtml(meta.logo) + '" alt="">'
        : '<div class="ch-fallback">' + escapeHtml((meta.name || "TV").slice(0, 2).toUpperCase()) + "</div>";
      card.innerHTML =
        logo +
        '<div class="ch-meta"><span class="ch-number">' + (meta.number || "") + '</span><span class="ch-name">' + escapeHtml(meta.name) + "</span></div>" +
        '<div class="ch-program">Señal en vivo</div>' +
        '<div class="ch-progress"><span></span></div>';
      var image = $("img", card);
      if (image) image.addEventListener("error", function () { image.style.display = "none"; });
      card.addEventListener("click", function () { playLive(meta.film); });
      var pressTimer = null;
      card.addEventListener("mousedown", function () {
        clearTimeout(pressTimer);
        pressTimer = setTimeout(function () {
          state.live.favorites[meta.code] = !state.live.favorites[meta.code];
          toast(state.live.favorites[meta.code] ? "Agregado a favoritos" : "Quitado de favoritos");
          writeJson("vp_webos_live_fav", state.live.favorites);
        }, 750);
      });
      ["mouseup", "mouseleave"].forEach(function (event) {
        card.addEventListener(event, function () { clearTimeout(pressTimer); });
      });
      holder.appendChild(card);
    });
    updateLiveEpg();
  }

  /* ============================ deportes ============================ */

  var SPORTS_TOP_LEAGUES = [
    "Liga MX", "UEFA Champions League", "Premier League", "LaLiga", "Serie A",
    "Bundesliga", "Ligue 1", "Copa Libertadores", "Copa Sudamericana", "MLS",
    "NBA", "NFL", "MLB", "NHL", "F1", "MotoGP", "UFC",
  ];

  function openSports() {
    closeTopMenus();
    showScreen("sports");
    state.sports.sport = "principal";
    state.sports.day = "today";
    state.sports.league = "";
    state.sports.matches = [];
    state.sports.events = [];
    $("#sports-hero").innerHTML = '<div class="hero-loading"><div class="spinner"></div><span>Cargando agenda deportiva</span></div>';
    $("#sports-content").innerHTML = "";
    $("#sports-status").textContent = "Cargando deportes…";
    $("#sports-count").textContent = "";
    loadSports();
  }

  function loadSports() {
    if (state.sports.loading) return;
    state.sports.loading = true;
    Promise.all([
      request("sports", { days: 8 }, 45000).catch(function () { return { matches: [] }; }),
      request("events", { day: todayMexico(), days: 2 }, 45000).catch(function () { return { events: [] }; }),
    ])
      .then(function (results) {
        state.sports.matches = (results[0] && results[0].matches) || [];
        state.sports.events = (results[1] && results[1].events) || [];
        state.sports.loading = false;
        renderSportsFilters();
        renderSports();
      })
      .catch(function (error) {
        state.sports.loading = false;
        $("#sports-status").textContent = "No se pudieron cargar los deportes: " + error.message;
      });
  }

  function sportsLeagueName(match) {
    return (match && match.league && match.league.name) || "Deportes";
  }

  function sportsLeagueLogo(match) {
    return (match && match.league && match.league.logo) || "";
  }

  function sportsTeamName(team) {
    return (team && (team.name || team.short)) || "";
  }

  function sportsKickoff(match) {
    try { return new Date(match.kickoff).getTime(); } catch (error) { return 0; }
  }

  function sportsDateKey(ms) {
    try {
      return new Intl.DateTimeFormat("en-CA", {
        timeZone: "America/Mexico_City", year: "numeric", month: "2-digit", day: "2-digit",
      }).format(new Date(ms));
    } catch (error) { return ""; }
  }

  function sportsTimeLabel(match) {
    var ms = sportsKickoff(match);
    if (!ms) return "";
    var day = sportsDateKey(ms);
    var today = todayMexico();
    var tomorrow = new Date(Date.now() + 86400000);
    var tomorrowKey = sportsDateKey(tomorrow.getTime());
    var prefix = day === today ? "Hoy" : day === tomorrowKey ? "Mañana" : "";
    try {
      var time = new Intl.DateTimeFormat("es-MX", {
        hour: "2-digit", minute: "2-digit", hour12: false, timeZone: "America/Mexico_City",
      }).format(new Date(ms));
      return (prefix ? prefix + " " : "") + time;
    } catch (error) { return prefix; }
  }

  function sportsPhase(match) {
    var status = String((match && match.status) || "").toLowerCase();
    if (status === "in" || status === "live") return "live";
    if (status === "post" || status === "final") return "done";
    var ms = sportsKickoff(match);
    if (!ms) return "soon";
    var now = Date.now();
    if (ms <= now && now < ms + 3 * 60 * 60 * 1000) return "live";
    if (now >= ms + 3 * 60 * 60 * 1000) return "done";
    return "soon";
  }

  function sportsMatchesForFilter() {
    var day = state.sports.day;
    var today = todayMexico();
    var tomorrow = sportsDateKey(Date.now() + 86400000);
    var list = (state.sports.matches || []).filter(function (match) {
      var league = sportsLeagueName(match);
      if (state.sports.sport === "principal") {
        if (!SPORTS_TOP_LEAGUES.some(function (name) { return league.toLowerCase() === name.toLowerCase(); })) return false;
      } else if (state.sports.sport === "futbol") {
        if (sportsPhase(match) === "done" && day === "today") { /* se conserva para la sección terminados */ }
      } else if (state.sports.sport === "basquet") {
        if (!/NBA|Basket|Básquet|EuroLeague/i.test(league)) return false;
      } else if (state.sports.sport === "hockey") {
        if (!/NHL|Hockey/i.test(league)) return false;
      } else if (state.sports.sport === "favoritos") {
        var favorites = state.sports.favorites || {};
        var home = sportsTeamName(match.home);
        var away = sportsTeamName(match.away);
        if (!favorites[home] && !favorites[away]) return false;
      }
      if (state.sports.league && league !== state.sports.league) return false;
      var key = sportsDateKey(sportsKickoff(match));
      if (day === "today" && key !== today) return false;
      if (day === "tomorrow" && key !== tomorrow) return false;
      return true;
    });
    return list;
  }

  function renderSportsFilters() {
    var sports = [
      ["principal", "Principal", "#i-filter"],
      ["futbol", "Fútbol", "#i-trophy"],
      ["basquet", "Básquet", "#i-basket"],
      ["hockey", "Hockey", "#i-hockey"],
      ["favoritos", "Favoritos", "#i-star"],
      ["buscar", "Buscar", "#i-search"],
      ["canales", "Canales", "#i-tv"],
    ];
    var holder = $("#sports-tabs");
    holder.innerHTML = "";
    sports.forEach(function (entry) {
      var button = el("button", "vt-sport-pill" + (state.sports.sport === entry[0] ? " is-active" : ""));
      button.setAttribute("data-focusable", "true");
      button.innerHTML = '<svg class="icon"><use href="' + entry[2] + '"/></svg><span>' + entry[1] + "</span>";
      button.addEventListener("click", function () {
        if (entry[0] === "buscar") { openSearch(); return; }
        if (entry[0] === "canales") { openLive(); return; }
        state.sports.sport = entry[0];
        state.sports.league = "";
        renderSportsFilters();
        renderSports();
      });
      holder.appendChild(button);
    });

    var days = [["today", "Hoy"], ["tomorrow", "Mañana"], ["all", "Todas"]];
    var dayHolder = $("#sports-days");
    dayHolder.innerHTML = "";
    days.forEach(function (entry) {
      var button = el("button", "vt-day-pill" + (state.sports.day === entry[0] ? " is-active" : ""));
      button.setAttribute("data-focusable", "true");
      button.textContent = entry[1];
      button.addEventListener("click", function () {
        state.sports.day = entry[0];
        renderSportsFilters();
        renderSports();
      });
      dayHolder.appendChild(button);
    });

    var leagues = [];
    (state.sports.matches || []).forEach(function (match) {
      var league = sportsLeagueName(match);
      if (league && leagues.indexOf(league) < 0) leagues.push(league);
    });
    leagues.sort(function (a, b) {
      var ai = SPORTS_TOP_LEAGUES.indexOf(a);
      var bi = SPORTS_TOP_LEAGUES.indexOf(b);
      if (ai < 0) ai = 99;
      if (bi < 0) bi = 99;
      return ai - bi || a.localeCompare(b);
    });
    var leagueHolder = $("#sports-leagues");
    leagueHolder.innerHTML = "";
    var allButton = el("button", "vt-league-pill" + (!state.sports.league ? " is-active" : ""));
    allButton.setAttribute("data-focusable", "true");
    allButton.textContent = "Todas las ligas";
    allButton.addEventListener("click", function () {
      state.sports.league = "";
      renderSportsFilters();
      renderSports();
    });
    leagueHolder.appendChild(allButton);
    leagues.slice(0, 12).forEach(function (league) {
      var button = el("button", "vt-league-pill" + (state.sports.league === league ? " is-active" : ""));
      button.setAttribute("data-focusable", "true");
      button.textContent = league;
      button.addEventListener("click", function () {
        state.sports.league = league;
        renderSportsFilters();
        renderSports();
      });
      leagueHolder.appendChild(button);
    });
  }

  function renderSports() {
    var matches = sportsMatchesForFilter();
    var live = matches.filter(function (m) { return sportsPhase(m) === "live"; });
    var soon = matches.filter(function (m) { return sportsPhase(m) === "soon"; });
    var done = matches.filter(function (m) { return sportsPhase(m) === "done"; });
    var heroMatch = live[0] || soon[0] || done[0] || null;
    renderSportsHero(heroMatch);
    var holder = $("#sports-content");
    holder.innerHTML = "";
    var total = matches.length;
    $("#sports-status").textContent = "";
    $("#sports-count").textContent = "";

    function group(label, list) {
      if (!list.length) return;
      var head = el("div", "vt-sports-group");
      head.innerHTML = "<span>" + escapeHtml(label) + "</span><b>" + list.length + "</b>";
      holder.appendChild(head);
      list.slice(0, 30).forEach(function (match) { holder.appendChild(buildMatchCard(match)); });
    }
    group("EN VIVO", live);
    group("COMIENZAN PRONTO", soon);
    group("TERMINADOS", done);

    if (!total) {
      holder.innerHTML = '<div class="vt-sports-empty">No hay eventos para este filtro.</div>';
    }
    setTimeout(function () {
      var first = $("#sports-content .vt-match-card");
      if (first) Focus.set(first);
    }, 40);
  }

  function renderSportsHero(match) {
    var hero = $("#sports-hero");
    if (!match) {
      hero.innerHTML = '<div class="hero-loading"><span>Sin eventos en la agenda.</span></div>';
      return;
    }
    var phase = sportsPhase(match);
    var status = phase === "live" ? "EN VIVO" : phase === "done" ? "FINAL" : "PRÓXIMO";
    var home = match.home || {};
    var away = match.away || {};
    var channels = (match.broadcasts && match.broadcasts.length ? match.broadcasts : match.channels) || [];
    hero.innerHTML =
      '<div class="sh-top"><span class="' + (phase === "live" ? "sh-live" : "sh-next") + '">' + status + "</span>" +
      '<span>' + escapeHtml(sportsLeagueName(match)) + '</span><span class="sh-time">' + escapeHtml(sportsTimeLabel(match)) + "</span></div>" +
      '<div class="sh-title">' + escapeHtml(sportsTeamName(home) + " vs " + sportsTeamName(away)) + "</div>" +
      '<div class="sh-tv">TV: ' + escapeHtml(channels.slice(0, 4).join(" · ") || "Sin señal por ahora") + "</div>" +
      '<div class="sh-actions"><button data-focusable id="sports-hero-play">Ver partido</button><button class="secondary" data-focusable id="sports-hero-agenda">Agenda</button></div>' +
      '<div class="sh-teams">' +
      '<div class="sh-team">' + (home.logo ? '<img src="' + escapeHtml(home.logo) + '" alt="">' : '<div class="mc-badge">' + escapeHtml((sportsTeamName(home) || "?").slice(0, 2)) + "</div>") + "<span>" + escapeHtml(sportsTeamName(home)) + "</span></div>" +
      '<div class="sh-vs">VS</div>' +
      '<div class="sh-team">' + (away.logo ? '<img src="' + escapeHtml(away.logo) + '" alt="">' : '<div class="mc-badge">' + escapeHtml((sportsTeamName(away) || "?").slice(0, 2)) + "</div>") + "<span>" + escapeHtml(sportsTeamName(away)) + "</span></div>" +
      "</div>";
    var play = $("#sports-hero-play");
    if (play) play.addEventListener("click", function () { openSportsDialog(match); });
    var agenda = $("#sports-hero-agenda");
    if (agenda) agenda.addEventListener("click", function () {
      state.sports.day = "all";
      renderSportsFilters();
      renderSports();
      Focus.first("#sports-content .vt-match-card");
    });
  }

  function buildMatchCard(match) {
    var card = el("button", "vt-match-card");
    card.setAttribute("data-focusable", "true");
    var home = match.home || {};
    var away = match.away || {};
    var phase = sportsPhase(match);
    var leagueLogo = sportsLeagueLogo(match);
    var channels = (match.broadcasts && match.broadcasts.length ? match.broadcasts : match.channels) || [];
    var scoreHome = home.score === null || home.score === undefined ? "-" : home.score;
    var scoreAway = away.score === null || away.score === undefined ? "-" : away.score;
    var score = phase === "live" || phase === "done" ? scoreHome + " - " + scoreAway : "-";
    function teamRow(team, score) {
      var name = sportsTeamName(team);
      var logo = team.logo ? '<img loading="lazy" src="' + escapeHtml(team.logo) + '" alt="">' : '<span class="mc-badge">' + escapeHtml((name || "?").slice(0, 2).toUpperCase()) + "</span>";
      return '<div class="mc-team">' + logo + "<span>" + escapeHtml(name) + '</span><span class="mc-score">' + escapeHtml(score) + "</span></div>";
    }
    card.innerHTML =
      '<div class="mc-top"><div class="mc-league">' +
      (leagueLogo ? '<img loading="lazy" src="' + escapeHtml(leagueLogo) + '" alt="">' : "") +
      "<span>" + escapeHtml(sportsLeagueName(match)) + "</span></div>" +
      '<span class="mc-time">' + escapeHtml(sportsTimeLabel(match)) + "</span></div>" +
      teamRow(home, scoreHome) + teamRow(away, scoreAway) +
      '<div class="mc-tv">TV: ' + escapeHtml(channels.slice(0, 3).join(" · ") || "Sin señal por ahora") + "</div>" +
      (phase === "live" ? '<div class="mc-ready">EN VIVO</div>' : "");
    card.addEventListener("click", function () { openSportsDialog(match); });
    return card;
  }

  function findEventForMatch(match) {
    var home = liveNorm(sportsTeamName(match.home));
    var away = liveNorm(sportsTeamName(match.away));
    if (!home || !away) return null;
    var candidates = state.sports.events || [];
    for (var i = 0; i < candidates.length; i += 1) {
      var event = candidates[i];
      var text = liveNorm((event.team_a || "") + " " + (event.team_b || "") + " " + (event.home_name || "") + " " + (event.away_name || "") + " " + (event.event || ""));
      if (text.indexOf(home) >= 0 && text.indexOf(away) >= 0) return event;
      if (text.indexOf(away) >= 0 && text.indexOf(home) >= 0) return event;
    }
    return null;
  }

  function openSportsDialog(match) {
    state.sports.dialogReturn = Focus.current;
    var dialog = $("#sports-dialog");
    var list = $("#sports-dialog-list");
    var phase = sportsPhase(match);
    var event = findEventForMatch(match);
    var info = buildSportInfo(match, event);
    var links = ((event && event.links) || []).filter(function (link) {
      return link && (link.name || link.url || link.link || link.token_api);
    });
    var channels = (match.broadcasts && match.broadcasts.length ? match.broadcasts : match.channels) || [];
    if ((!channels || !channels.length) && event && event.broadcasts) channels = event.broadcasts;
    info.guide = channels;
    $("#sports-dialog-title").textContent = sportsTeamName(match.home) + " vs " + sportsTeamName(match.away);
    var sub = sportsLeagueName(match) + " · " + sportsTimeLabel(match) + (info.scoreLine && info.scoreLine !== "VS" ? " · " + info.scoreLine : "");
    list.innerHTML = '<div class="dialog-sub">' + escapeHtml(sub) + "</div>";

    if (phase === "live" || phase === "done") {
      var statsButton = el("button", "link-row match-stats-row");
      statsButton.setAttribute("data-focusable", "true");
      statsButton.innerHTML = '<span class="match-stats-icon">▥</span><span class="match-stats-copy"><b>Estadísticas del partido</b><small>Match Center · resumen, cifras, goles y alineaciones</small></span><span class="link-type">Abrir</span>';
      statsButton.addEventListener("click", function () { openMatchCenter(info, 1, state.sports.dialogReturn); });
      list.appendChild(statsButton);
    }

    if (phase !== "live" && phase !== "done") {
      var note = el("div", "vt-reminder-note");
      note.textContent = "Aún no comienza · podrás verlo cuando el partido esté en vivo.";
      list.appendChild(note);
      var reminder = el("div", "vt-reminder-note");
      reminder.textContent = "Avisarme antes:";
      list.appendChild(reminder);
      var pills = el("div", "vt-reminder-pills");
      [10, 30, 60].forEach(function (mins) {
        var b = el("button", "", mins + " min");
        b.setAttribute("data-focusable", "true");
        b.addEventListener("click", function () { toast("Aviso programado " + mins + " min antes."); });
        pills.appendChild(b);
      });
      list.appendChild(pills);
      if (channels.length) {
        var tv = el("div", "vt-reminder-note");
        tv.textContent = "TV: " + channels.join(" · ");
        list.appendChild(tv);
      }
      var fav = el("button", "secondary-btn", "Favorito");
      fav.setAttribute("data-focusable", "true");
      fav.addEventListener("click", function () {
        var key = sportsTeamName(match.home);
        state.sports.favorites[key] = !state.sports.favorites[key];
        toast(state.sports.favorites[key] ? "Equipo favorito guardado." : "Equipo quitado de favoritos.");
        writeJson("vp_webos_sports_fav", state.sports.favorites);
      });
      list.appendChild(fav);
    } else if (links.length) {
      var available = el("div", "vt-reminder-note", "Transmisiones disponibles:");
      list.appendChild(available);
      links.slice(0, 40).forEach(function (link) {
        var row = el("button", "link-row");
        row.setAttribute("data-focusable", "true");
        var parsed = sportsLinkUrl(link);
        var alive = link.alive === true ? "Disponible" : link.alive === false ? "Caído" : "Sin verificar";
        var type = String(link.type || "HLS").toUpperCase();
        if (link.drm) type += " · ClearKey";
        if (!parsed.url && link.token_api) type = "Se resuelve al tocar";
        else if (!parsed.url) type = "Sin señal";
        row.innerHTML = '<span class="link-name">' + escapeHtml(link.name || "Señal") + "</span>" +
          '<span class="link-type">' + escapeHtml(type + " · " + (parsed.url ? alive : "Declarada")) + "</span>";
        row.addEventListener("click", function () {
          if (row.dataset.busy === "1") return;
          function start(url, headers, drmKey) {
            if (!url) { toast("Esta señal aún no publica dirección."); return; }
            info.signalName = link.name || "Señal";
            info.signalType = type;
            closeSportsDialog(false);
            playExternal(
              sportsTeamName(match.home) + " vs " + sportsTeamName(match.away),
              url,
              headers || {},
              info,
              drmKey || ""
            );
          }
          if (parsed.url) {
            start(parsed.url, parsed.headers, link.drm || "");
            return;
          }
          if (!link.token_api) {
            toast("Esta señal aún no publica dirección. Prueba otra fuente.");
            return;
          }
          row.dataset.busy = "1";
          row.classList.add("is-loading");
          row.querySelector(".link-type").textContent = "Resolviendo…";
          request("resolveEmbed", { tokenApi: link.token_api }, 45000)
            .then(function (resolved) {
              if (!resolved || !resolved.url) throw new Error("La fuente no publicó la señal.");
              var merged = Object.assign({}, parsed.headers, resolved.headers || {});
              start(resolved.url, merged, link.drm || "");
            })
            .catch(function (error) {
              row.dataset.busy = "";
              row.classList.remove("is-loading");
              row.querySelector(".link-type").textContent = type + " · Sin respuesta";
              toast("No se pudo resolver esa señal: " + error.message);
            });
        });
        list.appendChild(row);
      });
    } else {
      var none = el("div", "vt-reminder-note", "Sin señal por ahora. Canales que lo transmiten:");
      list.appendChild(none);
      channels.slice(0, 8).forEach(function (channel) {
        var row = el("div", "link-row static");
        row.innerHTML = '<span class="link-name">' + escapeHtml(channel) + '</span><span class="link-type">Sin señal</span>';
        list.appendChild(row);
      });
    }
    dialog.hidden = false;
    setTimeout(function () {
      var first = $("#sports-dialog-list [data-focusable]");
      if (first) Focus.set(first);
    }, 30);
  }

  function closeSportsDialog(restoreFocus) {
    var dialog = $("#sports-dialog");
    if (!dialog || dialog.hidden) return;
    dialog.hidden = true;
    if (restoreFocus !== false && state.sports.dialogReturn && document.contains(state.sports.dialogReturn)) {
      setTimeout(function () { Focus.set(state.sports.dialogReturn); }, 30);
    }
  }

  var MATCH_PAGES = [
    ["resumen", "Resumen"],
    ["estadisticas", "Estadísticas"],
    ["goles", "Goles y tarjetas"],
    ["alineaciones", "Alineaciones"],
  ];
  var MATCH_STAT_ORDER = [
    "Posesión", "Tiros", "Tiros a puerta", "Córners", "Faltas", "Amarillas",
    "Rojas", "Atajadas", "Fueras de juego", "Pases", "Precisión de pase",
  ];
  var MATCH_HUD_SUMMARY = ["Posesión", "Tiros", "Tiros a puerta", "Córners"];
  var MATCH_HUD_DISCIPLINE = ["Faltas", "Amarillas", "Rojas", "Fueras de juego"];

  function sportScoreLine(match, event) {
    var home = match && match.home ? match.home : {};
    var away = match && match.away ? match.away : {};
    var homeScore = home.score;
    var awayScore = away.score;
    if ((homeScore === null || homeScore === undefined) && event) homeScore = event.homeScore;
    if ((awayScore === null || awayScore === undefined) && event) awayScore = event.awayScore;
    var detail = (match && match.detail) || (event && (event.detail || event.status)) || "";
    if (homeScore !== null && homeScore !== undefined && awayScore !== null && awayScore !== undefined) {
      return homeScore + " - " + awayScore + (detail ? " · " + detail : "");
    }
    return "VS" + (detail ? " · " + detail : "");
  }

  function buildSportInfo(match, event) {
    var home = (match && match.home) || {};
    var away = (match && match.away) || {};
    var homeName = (event && (event.home_name || event.homeName)) || sportsTeamName(home);
    var awayName = (event && (event.away_name || event.awayName)) || sportsTeamName(away);
    var homeLogo = (event && (event.home_logo || event.homeLogo)) || home.logo || "";
    var awayLogo = (event && (event.away_logo || event.awayLogo)) || away.logo || "";
    var channels = (match && ((match.broadcasts && match.broadcasts.length) ? match.broadcasts : match.channels)) || [];
    if ((!channels || !channels.length) && event && event.broadcasts) channels = event.broadcasts;
    return {
      title: (homeName || "Local") + " vs " + (awayName || "Visitante"),
      league: (event && (event.league_label || event.leagueLabel)) || sportsLeagueName(match),
      scoreLine: sportScoreLine(match, event),
      guide: channels || [],
      homeName: homeName,
      awayName: awayName,
      homeLogo: homeLogo,
      awayLogo: awayLogo,
      homeScore: home.score,
      awayScore: away.score,
      dateIso: (event && (event.date_iso || event.dateIso)) || sportsDateKey(sportsKickoff(match)),
      leagueId: (match && match.league && match.league.id) || (event && (event.league_id || event.leagueId)) || "",
      eventId: (match && match.id) || (event && event.id) || "",
      signalName: "",
      signalType: "HLS",
      stats: null,
      loading: false,
      hudPage: 0,
    };
  }

  function sportsLinkUrl(link) {
    var primary = String((link && (link.url || link.link)) || "").trim();
    var pipeSource = String((link && (link.link || link.url)) || primary).trim();
    if (!primary && !pipeSource) return { url: "", headers: {} };
    var headers = Object.assign({}, (link && link.headers) || {});
    var pipe = pipeSource.indexOf("|");
    if (pipe >= 0) {
      var tail = pipeSource.slice(pipe + 1);
      if (!primary) primary = pipeSource.slice(0, pipe).trim();
      tail.split("&").forEach(function (part) {
        var cut = part.indexOf("=");
        if (cut <= 0) return;
        var key = part.slice(0, cut).trim().toLowerCase();
        var value = part.slice(cut + 1).trim();
        if (key && value && !headers[key]) headers[key] = value;
      });
    }
    return { url: primary, headers: headers };
  }

  function sportStatsKey(info) {
    return [info.eventId, info.leagueId, info.dateIso, info.homeName, info.awayName].join("|");
  }

  function loadSportStats(info) {
    if (!info) return Promise.resolve(null);
    if (info.stats) return Promise.resolve(info.stats);
    var key = sportStatsKey(info);
    var cached = state.sports.statsCache[key];
    if (cached && cached.promise) return cached.promise;
    if (cached && cached.stats) {
      info.stats = cached.stats;
      return Promise.resolve(cached.stats);
    }
    info.loading = true;
    var promise = request("matchStats", {
      eventId: info.eventId || "",
      leagueId: info.leagueId || "",
      dateIso: info.dateIso || "",
      homeName: info.homeName || "",
      awayName: info.awayName || "",
    }, 35000)
      .then(function (payload) {
        var stats = (payload && payload.stats) || null;
        state.sports.statsCache[key] = { at: Date.now(), stats: stats };
        info.stats = stats;
        info.loading = false;
        info.error = stats ? "" : "La fuente aún no publica estadísticas de este partido.";
        return stats;
      })
      .catch(function (error) {
        state.sports.statsCache[key] = { at: Date.now(), stats: null };
        info.stats = null;
        info.loading = false;
        info.error = error.message || "No se pudieron cargar las estadísticas.";
        return null;
      });
    state.sports.statsCache[key] = { at: Date.now(), promise: promise };
    return promise;
  }

  function matchStatLabels(stats, wanted) {
    if (!stats) return [];
    var values = Object.assign({}, (stats.home && stats.home.values) || {}, (stats.away && stats.away.values) || {});
    var known = (wanted || MATCH_STAT_ORDER).filter(function (label) {
      return values[label] !== undefined && values[label] !== "";
    });
    var extras = Object.keys(values).filter(function (label) {
      return known.indexOf(label) < 0 && values[label] !== undefined && values[label] !== "";
    });
    return known.concat(wanted ? [] : extras);
  }

  function statWeight(value) {
    var number = parseFloat(String(value === undefined || value === null ? "" : value).replace(/[^0-9.,-]/g, "").replace(",", "."));
    return isFinite(number) ? Math.abs(number) : 0;
  }

  function matchStatRow(label, home, away) {
    var homeValue = home || "—";
    var awayValue = away || "—";
    var total = statWeight(homeValue) + statWeight(awayValue);
    var fraction = total > 0 ? Math.max(0.12, Math.min(0.88, statWeight(homeValue) / total)) : 0.5;
    return '<div class="mcd-stat">' +
      '<div class="mcd-stat-line"><b>' + escapeHtml(homeValue) + "</b><span>" + escapeHtml(label) + "</span><b>" + escapeHtml(awayValue) + "</b></div>" +
      '<div class="mcd-bar"><i style="width:' + (fraction * 100).toFixed(1) + '%"></i><em style="width:' + ((1 - fraction) * 100).toFixed(1) + '%"></em></div>' +
      "</div>";
  }

  function matchEventLine(event) {
    var type = event.type === "GOAL" ? "GOL" : event.type === "RED" ? "ROJA" : event.type === "YELLOW" ? "AMAR" : "CAMBIO";
    var tone = event.type === "GOAL" ? "goal" : event.type === "RED" ? "red" : event.type === "YELLOW" ? "yellow" : "sub";
    return '<div class="mcd-event"><span class="mcd-minute">' + escapeHtml(event.minute || "") + '</span><b class="mcd-tag ' + tone + '">' + type + "</b>" +
      '<span class="mcd-event-player">' + escapeHtml(event.player || "") + (event.detail ? " · " + escapeHtml(event.detail) : "") + "</span>" +
      '<span class="mcd-event-team">' + escapeHtml(event.team || "") + "</span></div>";
  }

  function matchStatsBody(stats, page, compact) {
    if (!stats) {
      return '<div class="mcd-empty">' + (state.matchCenter.loading || (state.player.sport && state.player.sport.loading)
        ? "Cargando estadísticas del partido…"
        : "La fuente aún no publica estadísticas de este partido.") + "</div>";
    }
    if (page === 1 || page === "estadisticas") {
      var labels = matchStatLabels(stats, null);
      if (!labels.length) return '<div class="mcd-empty">Sin cifras publicadas todavía.</div>';
      return '<div class="mcd-stats">' + labels.map(function (label) {
        return matchStatRow(label, (stats.home.values || {})[label], (stats.away.values || {})[label]);
      }).join("") + "</div>";
    }
    if (page === 2 || page === "goles") {
      var events = stats.events || [];
      if (!events.length) return '<div class="mcd-empty">La fuente todavía no publica goles ni tarjetas.</div>';
      return '<div class="mcd-events">' + events.slice(0, 20).map(matchEventLine).join("") + "</div>";
    }
    if (page === 3 || page === "alineaciones") {
      var homePlayers = (stats.home && stats.home.players) || [];
      var awayPlayers = (stats.away && stats.away.players) || [];
      if (!homePlayers.length && !awayPlayers.length) return '<div class="mcd-empty">La fuente aún no publica alineaciones.</div>';
      function players(list) {
        return '<div class="mcd-lineup">' + list.map(function (player) {
          return '<div><b>' + escapeHtml(player.number || "") + "</b><span>" + escapeHtml(player.name || "") + "</span><em>" + escapeHtml(player.position || "") + "</em></div>";
        }).join("") + "</div>";
      }
      return '<div class="mcd-lineups"><div><h4>' + escapeHtml(stats.home.name) + " · LOCAL</h4>" + players(homePlayers) + "</div>" +
        '<div><h4>' + escapeHtml(stats.away.name) + " · VISITANTE</h4>" + players(awayPlayers) + "</div></div>";
    }
    var summaryLabels = compact ? matchStatLabels(stats, MATCH_HUD_SUMMARY) : matchStatLabels(stats, MATCH_STAT_ORDER).slice(0, 8);
    if (!summaryLabels.length) summaryLabels = matchStatLabels(stats, MATCH_STAT_ORDER).slice(0, compact ? 4 : 8);
    return '<div class="mcd-stats">' + summaryLabels.map(function (label) {
      return matchStatRow(label, (stats.home.values || {})[label], (stats.away.values || {})[label]);
    }).join("") + "</div>";
  }

  function renderMatchHudTabs() {
    var holder = $("#mh-tabs");
    if (!holder || holder.dataset.ready) return;
    holder.dataset.ready = "1";
    holder.innerHTML = MATCH_PAGES.map(function (entry) {
      return '<button class="mh-tab" data-focusable data-mh-page="' + entry[0] + '">' + entry[1] + "</button>";
    }).join("");
    $$("#mh-tabs .mh-tab").forEach(function (button) {
      button.addEventListener("click", function () {
        var sport = state.player.sport;
        if (!sport) return;
        sport.hudPage = MATCH_PAGES.findIndex(function (entry) { return entry[0] === button.getAttribute("data-mh-page"); });
        renderMatchHud();
        Focus.set(button);
      });
    });
  }

  function renderMatchHud() {
    var hud = $("#match-hud");
    var sport = state.player.sport;
    var visible = Boolean(hud && sport && state.screen === "player" && $("#screen-player").classList.contains("show-osd"));
    if (!hud) return;
    hud.hidden = !visible;
    if (!visible || !sport) return;
    renderMatchHudTabs();
    var stats = sport.stats || null;
    var page = Number(sport.hudPage) || 0;
    $("#mh-league").textContent = sport.league || "Deportes en vivo";
    $("#mh-scoreline").textContent = sport.scoreLine || "VS";
    $("#mh-home-name").textContent = (stats && stats.home && stats.home.name) || sport.homeName || "Local";
    $("#mh-away-name").textContent = (stats && stats.away && stats.away.name) || sport.awayName || "Visitante";
    var homeLogo = $("#mh-home-logo");
    var awayLogo = $("#mh-away-logo");
    var homeUrl = (stats && stats.home && stats.home.logo) || sport.homeLogo || "";
    var awayUrl = (stats && stats.away && stats.away.logo) || sport.awayLogo || "";
    homeLogo.hidden = !homeUrl;
    awayLogo.hidden = !awayUrl;
    if (homeUrl) homeLogo.src = homeUrl;
    if (awayUrl) awayLogo.src = awayUrl;
    $("#mh-page-label").textContent = (page + 1) + "/" + MATCH_PAGES.length + " · " + MATCH_PAGES[page][1];
    $("#mh-signal").textContent = sport.signalName ? "Señal: " + sport.signalName : "Match Center";
    $$("#mh-tabs .mh-tab").forEach(function (button, index) {
      button.classList.toggle("is-active", index === page);
    });
    $("#mh-body").innerHTML = sport.loading && !stats
      ? '<div class="mcd-empty">Cargando estadísticas del partido…</div>'
      : matchStatsBody(stats, page, true);
  }

  function renderMatchCenterTabs() {
    var holder = $("#mcd-tabs");
    if (!holder || holder.dataset.ready) return;
    holder.dataset.ready = "1";
    holder.innerHTML = MATCH_PAGES.map(function (entry, index) {
      return '<button class="mcd-tab" data-focusable data-mcd-page="' + index + '">' + entry[1] + "</button>";
    }).join("");
    $$("#mcd-tabs .mcd-tab").forEach(function (button) {
      button.addEventListener("click", function () {
        state.matchCenter.page = Number(button.getAttribute("data-mcd-page")) || 0;
        renderMatchCenter();
        Focus.set(button);
      });
    });
  }

  function renderMatchCenter() {
    var dialog = $("#match-center-dialog");
    var info = state.matchCenter.info;
    if (!dialog || !info) return;
    renderMatchCenterTabs();
    var stats = info.stats || null;
    $("#mcd-title").textContent = info.title || "Partido";
    $("#mcd-league").textContent = info.league || "Deportes en vivo";
    $("#mcd-scoreline").textContent = info.scoreLine || "VS";
    var centerScore = String(info.scoreLine || "VS").split("·")[0].trim() || "VS";
    var centerNode = $("#match-center-dialog .mcd-vs");
    if (centerNode) centerNode.textContent = centerScore;
    $("#mcd-home-name").textContent = (stats && stats.home && stats.home.name) || info.homeName || "Local";
    $("#mcd-away-name").textContent = (stats && stats.away && stats.away.name) || info.awayName || "Visitante";
    var homeUrl = (stats && stats.home && stats.home.logo) || info.homeLogo || "";
    var awayUrl = (stats && stats.away && stats.away.logo) || info.awayLogo || "";
    var homeLogo = $("#mcd-home-logo");
    var awayLogo = $("#mcd-away-logo");
    homeLogo.hidden = !homeUrl;
    awayLogo.hidden = !awayUrl;
    if (homeUrl) homeLogo.src = homeUrl;
    if (awayUrl) awayLogo.src = awayUrl;
    var page = Number(state.matchCenter.page) || 0;
    $$("#mcd-tabs .mcd-tab").forEach(function (button, index) {
      button.classList.toggle("is-active", index === page);
    });
    $("#mcd-body").innerHTML = info.loading && !stats
      ? '<div class="mcd-empty">Cargando estadísticas del partido…</div>'
      : (info.error && !stats ? '<div class="mcd-empty">' + escapeHtml(info.error) + "</div>" : matchStatsBody(stats, page, false));
    $("#mcd-source").textContent = info.signalName ? "Señal: " + info.signalName : "Datos: ESPN · GeeSports";
  }

  function openMatchCenter(info, page, returnFocus) {
    if (!info) {
      toast("Este partido no tiene datos para el centro de estadísticas.");
      return;
    }
    state.matchCenter.info = info;
    state.matchCenter.stats = info.stats || null;
    state.matchCenter.page = page === undefined ? 1 : Number(page) || 0;
    state.matchCenter.returnFocus = returnFocus || Focus.current;
    state.matchCenter.loading = !info.stats;
    state.matchCenter.error = info.error || "";
    if ($("#sports-dialog") && !$("#sports-dialog").hidden) closeSportsDialog(false);
    $("#match-center-dialog").hidden = false;
    renderMatchCenter();
    loadSportStats(info).then(function () {
      state.matchCenter.loading = false;
      state.matchCenter.stats = info.stats;
      state.matchCenter.error = info.error || "";
      if (!$("#match-center-dialog").hidden) renderMatchCenter();
      renderMatchHud();
    });
    setTimeout(function () {
      var first = $("#mcd-tabs .mcd-tab.is-active") || $("#match-center-dialog [data-focusable]");
      if (first) Focus.set(first);
    }, 50);
  }

  function closeMatchCenter(restoreFocus) {
    var dialog = $("#match-center-dialog");
    if (!dialog || dialog.hidden) return;
    dialog.hidden = true;
    var previous = state.matchCenter.returnFocus;
    if (restoreFocus !== false) {
      if (previous && document.contains(previous) && Focus.visible(previous)) setTimeout(function () { Focus.set(previous); }, 30);
      else if (state.screen === "player") {
        if ($("#screen-player").classList.contains("show-osd") && !$("#osd-stats").hidden) {
          setTimeout(function () { Focus.set($("#osd-stats")); }, 30);
        } else {
          Focus.clear();
        }
      }
    }
  }

  function playExternal(title, url, headers, sportInfo, drmKey) {
    var film = {
      id: "sport:" + Date.now(),
      title: title,
      videoUrl: url,
      kind: "live",
      externalUrl: url,
      headers: headers || {},
      drmKey: drmKey || "",
      sport: sportInfo || null,
      durationMin: 0,
    };
    openPlayer(film, null, null, true);
  }


  /* ============================ ajustes ============================ */

  function selectSettingsTab(tab) {
    $$("[data-settings-tab]").forEach(function (button) {
      button.classList.toggle("is-active", button.getAttribute("data-settings-tab") === tab);
    });
    $$("[data-settings-section]").forEach(function (section) {
      section.classList.toggle("is-active", section.getAttribute("data-settings-section") === tab);
    });
    setTimeout(function () {
      var active = $("[data-settings-tab].is-active");
      if (active) Focus.set(active);
    }, 30);
  }

  function openSettings() {
    showScreen("settings");
    updateSettingsUi();
    selectSettingsTab("reproduccion");
  }

  function updateSettingsUi() {
    $("#set-render-value").textContent = state.settings.render === "smooth" ? "Suave (ffmpeg)" : "Directo";
    $("#set-quality-value").textContent = state.settings.quality === "720p" ? "720p" : state.settings.quality === "1080p" ? "1080p" : "Auto";
    $("#set-subs-value").textContent =
      state.settings.subs === "off" ? "Desactivados" : state.settings.subs === "en" ? "English" : "Español";
    $("#set-intro-value").textContent = state.settings.skipIntro === "off" ? "Desactivado" : "Automático";
    $("#set-api").textContent = (state.serviceInfo && state.serviceInfo.api) || CFG.apiBase || "—";
    if (state.serviceInfo) {
      $("#set-service").textContent =
        (window.PalmServiceBridge ? "Luna" : "HTTP") +
        " · proxy " + (state.serviceInfo.proxyPort || "—") +
        (state.serviceInfo.relayAvailable ? " · ffmpeg listo" : " · ffmpeg no disponible");
    } else {
      $("#set-service").textContent = window.PalmServiceBridge ? "Luna" : "HTTP de desarrollo";
    }
    $("#about-version").textContent = VERSION;
  }

  /* ============================ OTA del ejecutable ============================ */

  var pcUpdate = {
    mode: "idle",
    info: null,
    manual: false,
    initialized: false,
    skippedVersion: "",
  };

  function pcUpdateSize(bytes) {
    var value = Number(bytes) || 0;
    if (value <= 0) return "";
    if (value >= 1024 * 1024) return (value / (1024 * 1024)).toFixed(1) + " MB";
    return Math.max(1, Math.round(value / 1024)) + " KB";
  }

  function pcUpdateSetBanner(visible, title, text, action, mode) {
    var banner = $("#pc-update-banner");
    if (!banner) return;
    banner.hidden = !visible;
    if (!visible) return;
    $("#pc-update-title").textContent = title || "Actualización disponible";
    $("#pc-update-text").textContent = text || "";
    var button = $("#pc-update-action");
    if (button) {
      button.textContent = action || "Actualizar";
      button.disabled = mode === "downloading" || mode === "installing";
    }
    if (mode) pcUpdate.mode = mode;
  }

  function pcUpdateSetSettings(text) {
    var node = $("#set-update-state");
    if (node) node.textContent = text || "Automático";
  }

  function handlePcUpdateStatus(status) {
    if (!status || !status.state) return;
    if (status.state === "checking") {
      pcUpdateSetSettings("Buscando…");
      return;
    }
    if (status.state === "available") {
      pcUpdate.info = status;
      pcUpdate.mode = "available";
      pcUpdateSetSettings("Disponible");
      pcUpdateSetBanner(
        true,
        "Actualización disponible · v" + status.version,
        "Se descargará y se instalará sin usar USB." + (status.size ? " · " + pcUpdateSize(status.size) : ""),
        "Actualizar",
        "available"
      );
      return;
    }
    if (status.state === "downloading") {
      pcUpdate.mode = "downloading";
      pcUpdateSetSettings("Descargando… " + (Number(status.percent) || 0) + "%");
      pcUpdateSetBanner(
        true,
        "Descargando Vision+ PC · v" + ((pcUpdate.info && pcUpdate.info.version) || status.version || ""),
        "No cierres la aplicación. " + (Number(status.percent) || 0) + "% completado.",
        "Descargando…",
        "downloading"
      );
      return;
    }
    if (status.state === "ready") {
      pcUpdate.info = Object.assign({}, pcUpdate.info || {}, status);
      pcUpdate.mode = "ready";
      pcUpdateSetSettings("Lista para instalar");
      pcUpdateSetBanner(
        true,
        "Actualización lista · v" + (status.version || ""),
        "La aplicación se reiniciará para instalar la nueva versión.",
        "Instalar y reiniciar",
        "ready"
      );
      return;
    }
    if (status.state === "installing") {
      pcUpdate.mode = "installing";
      pcUpdateSetSettings("Instalando…");
      pcUpdateSetBanner(true, "Instalando actualización", "Vision+ PC se reiniciará en un momento.", "Instalando…", "installing");
      return;
    }
    if (status.state === "latest") {
      pcUpdateSetSettings("Al día");
      if (pcUpdate.manual) toast("Vision+ PC ya está actualizado.");
      pcUpdate.manual = false;
      return;
    }
    if (status.state === "error") {
      pcUpdate.mode = "error";
      pcUpdateSetSettings("No disponible");
      if (pcUpdate.manual) toast("No se pudo actualizar: " + (status.message || "error de red"));
      pcUpdate.manual = false;
    }
  }

  function initPcUpdates() {
    if (pcUpdate.initialized) return;
    var api = window.visionPC;
    if (!api || !api.checkForUpdates) return;
    pcUpdate.initialized = true;
    if (api.onUpdateStatus) api.onUpdateStatus(handlePcUpdateStatus);
    if (api.getVersion) {
      api.getVersion()
        .then(function (version) {
          if (version) {
            var about = $("#about-version");
            if (about) about.textContent = version;
          }
        })
        .catch(function () {});
    }
    var checkButton = $("#set-check-update");
    if (checkButton) checkButton.addEventListener("click", function () {
      pcUpdate.manual = true;
      pcUpdateSetSettings("Buscando…");
      api.checkForUpdates().catch(function (error) {
        pcUpdate.manual = false;
        toast("No se pudo comprobar la actualización: " + error.message);
      });
    });
    var action = $("#pc-update-action");
    if (action) action.addEventListener("click", function () {
      if (pcUpdate.mode === "available") {
        api.downloadUpdate().catch(function (error) {
          toast("No se pudo descargar la actualización: " + error.message);
        });
      } else if (pcUpdate.mode === "ready") {
        api.installUpdate().catch(function (error) {
          toast("No se pudo instalar la actualización: " + error.message);
        });
      }
    });
    var later = $("#pc-update-later");
    if (later) later.addEventListener("click", function () {
      var version = (pcUpdate.info && pcUpdate.info.version) || "";
      if (version) pcUpdate.skippedVersion = version;
      pcUpdateSetBanner(false);
    });
    if (api.lastUpdateStatus) {
      api.lastUpdateStatus().then(function (last) {
        if (last) handlePcUpdateStatus(last);
      }).catch(function () {});
    }
    setTimeout(function () {
      if (pcUpdate.skippedVersion) return;
      api.checkForUpdates().catch(function () {});
    }, 6500);
  }

  function bindSettings() {
    $("#set-render").addEventListener("click", function () {
      state.settings.render = state.settings.render === "direct" ? "smooth" : "direct";
      writeJson("vp_webos_settings", state.settings);
      updateSettingsUi();
      toast(state.settings.render === "smooth" ? "Modo suave: ffmpeg remuxea el TS a MP4." : "Modo directo: el TS se reproduce tal cual.");
    });
    $("#set-quality").addEventListener("click", function () {
      var order = ["auto", "720p", "1080p"];
      var index = order.indexOf(state.settings.quality);
      state.settings.quality = order[(index + 1) % order.length];
      writeJson("vp_webos_settings", state.settings);
      updateSettingsUi();
      toast("Calidad preferida: " + (state.settings.quality === "auto" ? "Auto" : state.settings.quality));
    });
    $("#set-subs").addEventListener("click", function () {
      var order = ["es", "en", "off"];
      var index = order.indexOf(state.settings.subs);
      state.settings.subs = order[(index + 1) % order.length];
      writeJson("vp_webos_settings", state.settings);
      updateSettingsUi();
      toast(
        state.settings.subs === "off"
          ? "Subtítulos desactivados."
          : state.settings.subs === "en"
            ? "Subtítulos en inglés."
            : "Subtítulos en español.",
      );
    });
    $("#set-intro").addEventListener("click", function () {
      state.settings.skipIntro = state.settings.skipIntro === "off" ? "auto" : "off";
      writeJson("vp_webos_settings", state.settings);
      updateSettingsUi();
      toast(state.settings.skipIntro === "off" ? "Omitir intro desactivado." : "Omitir intro automático.");
    });
    $("#set-reset").addEventListener("click", function () {
      request("resetSession", {}, 15000)
        .then(function () {
          toast("Sesión del portal renovada.");
          loadServiceInfo();
        })
        .catch(function (error) {
          toast("No se pudo renovar: " + error.message);
        });
    });
    $("#set-cache").addEventListener("click", function () {
      request("clearCache", {}, 15000)
        .then(function () {
          toast("Caché del catálogo limpia.");
          loadHome();
        })
        .catch(function (error) {
          toast("No se pudo limpiar: " + error.message);
        });
    });
  }

  function loadServiceInfo() {
    request("ping", {}, 12000)
      .then(function (info) {
        state.serviceInfo = info;
        updateSettingsUi();
        if (state.screen === "player") applyPlayerOptions();
      })
      .catch(function () {
        state.serviceInfo = null;
        updateSettingsUi();
      });
  }

  /* ============================ navegación / teclado ============================ */

  function closeTopMenus(except) {
    ["menu-live", "menu-series", "menu-library"].forEach(function (id) {
      var node = document.getElementById(id);
      if (node && id !== except) node.hidden = true;
    });
  }

  function showTopMenu(id, focusSelector) {
    closeTopMenus(id);
    var menu = document.getElementById(id);
    if (!menu) return;
    menu.hidden = false;
    setTimeout(function () {
      var first = menu.querySelector("[data-focusable]");
      if (first) Focus.set(first);
    }, 25);
  }

  function topbarVisibleFor(name) {
    return name !== "splash" && name !== "player" && name !== "detail";
  }

  function updateTopbar(name) {
    var bar = $("#app-topbar");
    if (bar) bar.classList.toggle("vt-hidden", !topbarVisibleFor(name));
    var active = "";
    if (name === "home") active = "home";
    else if (name === "live" || name === "sports") active = "live-hub";
    else if (name === "library" || name === "history") active = "library-hub";
    else if (name === "grid") {
      if (state.topTab === "series" || state.topTab === "anime" || state.topTab === "novelas") active = "series-hub";
      else if (state.topTab === "library" || state.topTab === "history") active = "library-hub";
      else active = state.topTab || "movies";
    }
    else if (name === "search" || name === "settings") active = "";
    $$("#app-nav [data-nav]").forEach(function (button) {
      button.classList.toggle("is-active", button.getAttribute("data-nav") === active);
    });
    if (name !== "live" && name !== "sports") closeTopMenus();
    var countryMenu = $("#live-country-menu");
    if (countryMenu && name !== "live") countryMenu.hidden = true;
  }

  var WESTERN_ANIMATION = [
    "LOS SIMPSON", "PADRE DE FAMILIA", "FAMILY GUY", "FUTURAMA", "AMERICAN DAD",
    "BOB S BURGERS", "AVATAR", "MIRACULOUS", "TEEN TITANS", "JOVENES TITANES",
    "SCOOBY", "TOM Y JERRY", "LOONEY", "BEN 10", "ADVENTURE TIME", "HORA DE AVENTURA",
    "STEVEN UNIVERSE", "GRAVITY FALLS", "RICK AND MORTY", "SOUTH PARK", "SPONGEBOB",
    "BOB ESPONJA", "PAW PATROL", "PATRULLA DE CACHORROS", "MY LITTLE PONY",
    "TRANSFORMERS", "MONSTER HIGH", "BARBIE", "THOMAS FRIENDS", "MASHA",
    "CORALINE", "HOTEL TRANSYLVANIA", "POWER RANGERS", "LABORATORIO DE DEXTER",
    "DEXTER S LABORATORY", "HAZBIN HOTEL", "LOVE DEATH", "BATMAN", "SOLAR OPPOSITES",
    "FINAL SPACE", "SPIDEY", "ODO", "OPERATION OUCH", "ENC REN CAO", "PLANETA PREHISTORICO",
    "SHREK", "KUNG FU PANDA", "MADAGASCAR", "ICE AGE", "MINIONS", "DESPICABLE ME",
    "HOW TO TRAIN YOUR DRAGON", "TROLLS", "CLOUDY WITH A CHANCE", "THE BOSS BABY",
    "REGULAR SHOW", "GUMBALL", "YOUNG JUSTICE", "STAR WARS", "MARVEL", "DC SUPER",
    "UN SHOW MAS", "JURASSIC WORLD", "STRANGER THINGS", "EL HOTEL EMBRUJADO",
    "LA VACA Y EL POLLITO", "EL CADAVER DE LA NOVIA", "MEMORIZACION DE TOKENS",
  ];

  function isWesternAnimation(film) {
    var n = liveNorm(film && film.title);
    for (var i = 0; i < WESTERN_ANIMATION.length; i += 1) {
      if (n.indexOf(WESTERN_ANIMATION[i]) >= 0) return true;
    }
    return false;
  }

  function hasAnimationGenre(film) {
    var genres = (film && film.genres) || [];
    if (!genres.length) return true;
    return genres.some(function (genre) { return /animaci|anime/i.test(String(genre)); });
  }

  function looksLikeMovie(film) {
    if (!film) return false;
    if (film.kind === "movie") return true;
    return Number(film.durationMin) > 0 && !(film.seasons && film.seasons.length);
  }

  function flattenSection(data) {
    var rows = (data && data.rows) || [];
    var films = [];
    var seen = {};
    rows.forEach(function (row) {
      (row.films || []).forEach(function (film) {
        if (!film || !film.id || seen[film.id]) return;
        seen[film.id] = true;
        films.push(film);
      });
    });
    return films;
  }

  function openSection(name, title) {
    closeTopMenus();
    state.topTab = name;
    showScreen("grid", { noStack: true });
    $("#grid-title").textContent = title || name;
    $("#grid-items").innerHTML = '<div class="grid-loading">Cargando ' + escapeHtml(title || name) + "…</div>";
    state.grid = {
      title: title || name,
      items: [],
      page: 1,
      pageSize: 120,
      total: 0,
      categoryId: "",
      loader: null,
    };
    var requests = [request("section", { name: name, limit: 60 }, 35000)];
    if (name === "movies") {
      requests.push(request("section", { name: "anime", limit: 60 }, 35000).catch(function () { return { rows: [] }; }));
    }
    Promise.all(requests)
      .then(function (results) {
        var films = flattenSection(results[0]);
        if (name === "anime") {
          // Anime = series de animación japonesa; sus películas se muestran en Películas.
          films = films.filter(function (film) {
            return film.kind === "anime" && !looksLikeMovie(film) &&
              !isWesternAnimation(film) && hasAnimationGenre(film);
          });
        } else if (name === "series") {
          // Series = series, nunca películas ni anime.
          films = films.filter(function (film) { return film.kind === "series" && !looksLikeMovie(film); });
        } else if (name === "movies") {
          // Películas incluye cine de animación (las películas de anime van aquí).
          var extra = flattenSection(results[1]).filter(function (film) {
            return (film.kind === "anime" || film.kind === "kids") && looksLikeMovie(film);
          });
          var seen = {};
          films = films.filter(function (film) { return !film.kind || film.kind === "movie" || looksLikeMovie(film); });
          films.forEach(function (film) { seen[film.id] = true; });
          extra.forEach(function (film) {
            if (!seen[film.id]) { seen[film.id] = true; films.push(film); }
          });
        }
        state.grid.items = films;
        state.grid.total = films.length;
        renderGrid(films, false);
        setTimeout(function () {
          Focus.first("#grid-items .grid-card");
        }, 40);
      })
      .catch(function (error) {
        $("#grid-items").innerHTML = '<div class="grid-loading">No se pudo cargar: ' + escapeHtml(error.message) + "</div>";
      });
  }

  function openLibrary() {
    closeTopMenus();
    state.topTab = "library";
    var films = Object.keys(state.list || {}).map(function (key) {
      return state.list[key];
    }).filter(Boolean);
    openGrid({
      title: "Mi lista",
      films: films,
      empty: films.length ? "" : "Tu lista está vacía. Agrega títulos desde una ficha.",
    });
  }

  function openHistory() {
    closeTopMenus();
    state.topTab = "history";
    var films = [];
    var seen = {};
    Object.keys(state.progress || {}).forEach(function (key) {
      var entry = state.progress[key];
      if (!entry || !entry.film || !entry.film.id || seen[entry.film.id]) return;
      seen[entry.film.id] = true;
      films.push(entry.film);
    });
    openGrid({
      title: "Historial",
      films: films,
      empty: films.length ? "" : "Todavía no hay historial. Lo que veas aparecerá aquí.",
    });
  }

  function navigateTop(nav) {
    closeTopMenus();
    if (nav === "home") {
      showScreen("home", { noStack: true });
      setTimeout(function () { Focus.first("#home-hero .primary-btn"); }, 50);
    } else if (nav === "live-hub") {
      showTopMenu("menu-live");
    } else if (nav === "series-hub") {
      showTopMenu("menu-series");
    } else if (nav === "library-hub") {
      showTopMenu("menu-library");
    } else if (nav === "live") {
      openLive();
    } else if (nav === "sports") {
      openSports();
    } else if (nav === "movies") {
      openSection("movies", "Películas");
    } else if (nav === "series") {
      openSection("series", "Series");
    } else if (nav === "anime") {
      openSection("anime", "Anime");
    } else if (nav === "novelas") {
      openSection("novelas", "Novelas");
    } else if (nav === "library") {
      openLibrary();
    } else if (nav === "history") {
      openHistory();
    } else if (nav === "search") {
      openSearch();
    } else if (nav === "settings") {
      openSettings();
    }
  }

  function bindNavigation() {
    $$("#app-nav [data-nav], #app-topbar [data-nav]").forEach(function (button) {
      button.addEventListener("click", function () {
        navigateTop(button.getAttribute("data-nav"));
      });
    });
    var reload = $("#top-reload");
    if (reload) reload.addEventListener("click", function () {
      if (state.screen === "live") openLive();
      else if (state.screen === "sports") loadSports();
      else if (state.screen === "grid") loadGridPage(1, false);
      else loadHome();
      toast("Actualizando…");
    });
    var notifications = $("#top-notifications");
    if (notifications) notifications.addEventListener("click", function () {
      toast("No hay avisos nuevos.");
    });
    var profile = $("#top-profile");
    if (profile) profile.addEventListener("click", function () {
      toast("Perfil Principal · sesión activa");
    });
    $$("[data-back]").forEach(function (button) {
      button.addEventListener("click", function () { goBack(); });
    });
    $("#player-back").addEventListener("click", closePlayer);
    $("#osd-back").addEventListener("click", closePlayer);
    $("#osd-toggle").addEventListener("click", togglePlay);
    $("#osd-rew").addEventListener("click", function () { seekBy(-10); });
    $("#osd-ffw").addEventListener("click", function () { seekBy(10); });
    $("#osd-subs").addEventListener("click", cycleSubtitles);
    $("#osd-audio").addEventListener("click", cycleAudio);
    var imageButton = $("#osd-image");
    if (imageButton) imageButton.addEventListener("click", toggleImageMode);
    $("#osd-quality").addEventListener("click", cycleQuality);
    $("#grid-more").addEventListener("click", function () { loadGridPage(state.grid.page + 1, true); });
    var liveMore = $("#live-more");
    if (liveMore) liveMore.addEventListener("click", function () { toast("Ya se muestran todos los canales."); });
    var liveRefresh = $("#live-refresh");
    if (liveRefresh) liveRefresh.addEventListener("click", function () { openLive(); });
    var liveSearch = $("#live-search");
    if (liveSearch) liveSearch.addEventListener("click", function () { openSearch(); });
    var liveCountry = $("#live-country");
    if (liveCountry) liveCountry.addEventListener("click", openLiveCountryMenu);
    var liveFav = $("#live-fav");
    if (liveFav) liveFav.addEventListener("click", function () {
      state.live.category = state.live.category === "Favoritos" ? "Todo" : "Favoritos";
      filterLive();
    });
    $("#sports-dialog-close").addEventListener("click", function () { closeSportsDialog(); });
    $("#match-center-close").addEventListener("click", function () { closeMatchCenter(); });
    $("#osd-stats").addEventListener("click", function () {
      if (state.player.sport) openMatchCenter(state.player.sport, 1);
    });
    $("#mh-open").addEventListener("click", function () {
      if (state.player.sport) openMatchCenter(state.player.sport, 1);
    });
    $$("[data-settings-tab]").forEach(function (button) {
      button.addEventListener("click", function () {
        selectSettingsTab(button.getAttribute("data-settings-tab"));
      });
    });
  }

  function bindKeys() {
    document.addEventListener("keydown", function (event) {
      var code = event.keyCode;
      if (state.screen === "player") {
        handlePlayerKey(code, event);
        return;
      }
      if (code === 461 || code === 27) {
        event.preventDefault();
        if ($("#match-center-dialog") && !$("#match-center-dialog").hidden) {
          closeMatchCenter();
          return;
        }
        var openMenu = ["menu-live", "menu-series", "menu-library", "live-country-menu"].some(function (id) {
          var node = document.getElementById(id);
          return node && !node.hidden;
        });
        if (openMenu) {
          closeTopMenus();
          return;
        }
        goBack();
        return;
      }
      if (state.screen === "search" && code === 8) {
        event.preventDefault();
        searchKey("back");
        return;
      }
      if (code === 37 || code === 38 || code === 39 || code === 40) {
        event.preventDefault();
        if (state.screen === "search") state.search.physical = false;
        Focus.move(code === 37 ? "left" : code === 39 ? "right" : code === 38 ? "up" : "down");
        return;
      }
      if (code === 13) {
        event.preventDefault();
        if (state.screen === "search" && state.search.physical && state.search.query.trim()) {
          state.search.physical = false;
          searchKey("search");
          return;
        }
        Focus.activate();
        return;
      }
      if (
        state.screen === "search" &&
        event.key &&
        event.key.length === 1 &&
        event.key.charCodeAt(0) >= 32 &&
        !event.ctrlKey &&
        !event.metaKey &&
        !event.altKey
      ) {
        event.preventDefault();
        state.search.physical = true;
        searchKey(event.key);
      }
    });
  }

  /* ============================ video ============================ */

  function bindVideo() {
    var video = $("#player-video");
    video.addEventListener("loadedmetadata", function () {
      if (!isRelayPlayback() && Number(video.duration) && Number(video.duration) !== Infinity) {
        state.player.duration = Number(video.duration);
      }
      if (state.player.pendingSeek > 0 && !state.player.isLive && !isRelayPlayback()) {
        try {
          video.currentTime = state.player.pendingSeek;
        } catch (error) {
          /* noop */
        }
        state.player.pendingSeek = 0;
      }
      $("#player-loading").hidden = true;
      updateOsd();
    });
    video.addEventListener("playing", function () {
      $("#player-loading").hidden = true;
      clearTimeout(state.player.loadingTimer);
      updateOsd();
    });
    video.addEventListener("pause", updateOsd);
    video.addEventListener("timeupdate", function () {
      var current = playerPosition();
      updateOsd();
      updateSubtitle(current);
      maybeSkipIntro(current);
      saveProgress(false);
    });
    video.addEventListener("progress", updateOsd);
    video.addEventListener("error", function () {
      if (state.player.isLive) {
        $("#player-loading").hidden = true;
        showPlayerMessage("La señal en vivo no está disponible. Pulsa OK para reintentar.");
        state.player.retryHint = true;
        return;
      }
      if (!state.player.retrying && state.serviceInfo && state.serviceInfo.relayAvailable) {
        retrySmooth();
      } else {
        $("#player-loading").hidden = true;
        showPlayerMessage("No se pudo reproducir el archivo. Pulsa OK para reintentar.");
        state.player.retryHint = true;
      }
    });
    video.addEventListener("ended", function () {
      saveProgress(true);
      var film = state.player.film;
      var episode = state.player.episode;
      if (film && episode && state.detail.episodes && state.detail.episodes.length) {
        var index = state.detail.episodes.findIndex(function (item) {
          return item.contentId === episode.contentId;
        });
        if (index >= 0 && index + 1 < state.detail.episodes.length) {
          var next = state.detail.episodes[index + 1];
          playFilm(film, next, state.player.season || state.detail.seasons[state.detail.seasonIndex] || null);
          return;
        }
      }
      closePlayer();
    });
    var progressBar = $("#osd-progress");
    var seekHint = $("#osd-seek-hint");
    var scrubbing = false;

    function canScrub() {
      return state.screen === "player" && Boolean(state.player.film) && !state.player.isLive;
    }

    function ratioAt(clientX) {
      var rect = progressBar.getBoundingClientRect();
      return clamp((clientX - rect.left) / Math.max(1, rect.width), 0, 1);
    }

    function showSeekHint(ratio) {
      if (!seekHint || !progressBar) return;
      var duration = playerDuration();
      if (duration <= 0) {
        seekHint.hidden = true;
        return;
      }
      seekHint.textContent = formatTime(ratio * duration);
      seekHint.style.left = (ratio * 100).toFixed(2) + "%";
      seekHint.hidden = false;
    }

    function hideSeekHint() {
      if (seekHint) seekHint.hidden = true;
    }

    function previewScrub(event) {
      if (!canScrub()) return;
      var duration = playerDuration();
      if (duration <= 0) return;
      var ratio = ratioAt(event.clientX);
      state.player.scrubRatio = ratio;
      showSeekHint(ratio);
      updateOsd();
    }

    function finishScrub(event) {
      if (!scrubbing) return;
      scrubbing = false;
      var duration = playerDuration();
      var ratio = ratioAt(event.clientX);
      state.player.scrubRatio = null;
      hideSeekHint();
      if (duration > 0) seekToPosition(ratio * duration);
      else updateOsd();
    }

    if (progressBar) {
      progressBar.addEventListener("mousedown", function (event) {
        if (event.button !== 0 || !canScrub()) return;
        if (playerDuration() <= 0) return;
        event.preventDefault();
        scrubbing = true;
        previewScrub(event);
      });
      progressBar.addEventListener("mousemove", function (event) {
        if (!canScrub() || scrubbing) return;
        showSeekHint(ratioAt(event.clientX));
      });
      progressBar.addEventListener("mouseleave", function () {
        if (!scrubbing) hideSeekHint();
      });
      progressBar.addEventListener("click", function (event) {
        // El clic se resuelve en mouseup (abajo) para no seekear dos veces.
        event.stopPropagation();
      });
      document.addEventListener("mousemove", function (event) {
        if (scrubbing) previewScrub(event);
      });
      document.addEventListener("mouseup", function (event) {
        if (scrubbing) finishScrub(event);
      });
    }

    var playerScreen = $("#screen-player");
    if (playerScreen) {
      playerScreen.addEventListener("mousemove", function () {
        if (state.screen !== "player") return;
        var dialog = $("#match-center-dialog");
        if (dialog && !dialog.hidden) return;
        showOsd();
      });
      playerScreen.addEventListener("click", function (event) {
        if (event.button !== 0 || state.screen !== "player") return;
        var target = event.target;
        if (target && target.closest && target.closest(".player-osd, #match-hud, #match-center-dialog, .player-top, #player-loading, #player-message")) {
          return;
        }
        var dialog = $("#match-center-dialog");
        if (dialog && !dialog.hidden) return;
        if ($("#screen-player").classList.contains("show-osd")) hideOsd();
        else showOsd();
      });
    }
  }

  /* ============================ arranque ============================ */

  function setCopyText(selector, value) {
    var node = $(selector);
    if (node) node.textContent = value;
  }

  /**
   * La misma app se comparte con el port de PC. En LG no deben verse textos
   * de escritorio («ventana», «Chrome/Edge/Electron», «junto al ejecutable»).
   */
  function applyPlatformCopy() {
    if (PC) return;
    setCopyText("#set-screen-value", "1920 × 1080 (TV)");
    setCopyText("#set-screen-help", "La interfaz usa la resolución de la TV y se controla con el mando.");
    setCopyText("#set-engine-value", "Nativo webOS");
    setCopyText("#set-engine-codec", "H.265 / H.264 / HLS");
    setCopyText("#set-engine-help", "La TV reproduce el formato original del catálogo; no se transcodifica.");
    setCopyText("#set-account-help", "Vision+ para LG usa el perfil local Principal. Los perfiles y la continuidad se sincronizan desde la app del teléfono.");
    setCopyText("#set-update-title", "Versión de Vision+ para LG");
    setCopyText("#set-update-help", "Las actualizaciones de Vision+ para LG se instalan desde la app del teléfono.");
    setCopyText("#set-data-help", "La sesión y la caché viven en el almacenamiento de la app en la TV.");
    setCopyText("#about-line", "Catálogo VPS, resolución directa del portal XTV y proxy local. Reproductor nativo webOS.");
    setCopyText("#about-version", VERSION);
    var checkUpdate = $("#set-check-update");
    if (checkUpdate) checkUpdate.hidden = true;
  }

  function boot() {
    state.settings = readJson("vp_webos_settings", { render: "direct", quality: "auto", subs: "es", skipIntro: "auto" });
    if (!state.settings.skipIntro) state.settings.skipIntro = "auto";
    if (PC) {
      var splashVersion = $(".splash-version");
      if (splashVersion) splashVersion.textContent = "PC";
      var renderSetting = $("#set-render");
      if (renderSetting) renderSetting.hidden = true;
      var aboutLine = $(".about-line");
      if (aboutLine) {
        aboutLine.innerHTML = 'Vision+ para PC <span id="about-version"></span>';
        var aboutVersion = $("#about-version");
        if (aboutVersion) aboutVersion.textContent = VERSION;
      }
    }
    applyPlatformCopy();
    state.progress = readJson("vp_webos_progress", {});
    state.list = readJson("vp_webos_list", {});
    state.live.favorites = readJson("vp_webos_live_fav", {});
    state.sports.favorites = readJson("vp_webos_sports_fav", {});
    bindNavigation();
    bindKeys();
    bindSettings();
    bindVideo();
    clockTick();
    setInterval(clockTick, 30000);
    loadServiceInfo();
    loadHome();
    setTimeout(function () {
      var initial = $("#hero-play") || $("#home-retry");
      showScreen("home", { noStack: true, focus: initial || null });
      var requested = "";
      try { requested = (String(location.search || "").match(/[?&]screen=([a-z]+)/) || [])[1] || ""; } catch (error) { requested = ""; }
      if (requested === "live") setTimeout(openLive, 900);
      else if (requested === "sports") setTimeout(openSports, 900);
      else if (requested === "settings") setTimeout(openSettings, 900);
      else if (requested === "search") setTimeout(openSearch, 900);
      setTimeout(showFullscreenHint, 1600);
      setTimeout(initPcUpdates, 1800);
    }, 650);
  }

  window.__vp = {
    state: state,
    focus: Focus,
    showScreen: showScreen,
    openPlayer: openPlayer,
    closePlayer: closePlayer,
    playLive: playLive,
    openDetail: openDetail,
    openLive: openLive,
    openSports: openSports,
    openSportsDialog: openSportsDialog,
    openMatchCenter: openMatchCenter,
    buildSportInfo: buildSportInfo,
    openSettings: openSettings,
    openSection: openSection,
    openSearch: openSearch,
    loadEpisodes: loadEpisodes,
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
