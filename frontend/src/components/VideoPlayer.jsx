import React, { useEffect, useRef, useState, useMemo, useCallback } from 'react';
import Plyr from 'plyr';
import Hls from 'hls.js';
import { X, Play, Pause, RefreshCw, Layers, RotateCcw, RotateCw, SkipForward, SkipBack, List, Globe as Globe2, Subtitles, Volume2, VolumeX, Maximize2, Minimize2, FastForward } from 'lucide-react';
import 'plyr/dist/plyr.css';

// BRANDING PRE-ROLL CONFIGURATION
const BRAND_INTRO_CONFIG = {
  enabled: true,                  // Enable/disable the brand intro video
  videoUrl: '/brand-intro.mp4',   // Local path (frontend/public/) or remote URL
  skipDelay: 2,                   // Seconds before showing the Skip button (0 for immediate, null for unskippable)
  playOnTV: false,                // Show on Live TV channels?
  playOnEpisodeChange: false      // Show on every episode change?
};

export default function VideoPlayer({ source, onClose, onNext, onNextEpisode, onPrevEpisode, channelList, onChannelChange }) {
  const [activeStreamIndex, setActiveStreamIndex] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [errorText, setErrorText] = useState(source && source.available === false ? source.unavailableReason : null);

  const [showControls, setShowControls] = useState(true);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [playerKey, setPlayerKey] = useState(0);

  const [availableAudioTracks, setAvailableAudioTracks] = useState([]);
  const [activeAudioTrack, setActiveAudioTrack] = useState(-1);
  const [availableSubtitleTracks, setAvailableSubtitleTracks] = useState([]);
  const [activeSubtitleTrack, setActiveSubtitleTrack] = useState(-1);
  
  const [localSource, setLocalSource] = useState(source);
  const [retryCount, setRetryCount] = useState(0);
  const [showDebugInfo, setShowDebugInfo] = useState(false);
  const [showChannelGuide, setShowChannelGuide] = useState(false);
  const [channelInfo, setChannelInfo] = useState(null);
  const [volume, setVolume] = useState(1);
  const [isMuted, setIsMuted] = useState(false);
  const [playbackRate, setPlaybackRate] = useState(1);
  const [showEndScreen, setShowEndScreen] = useState(false);
  const channelInfoTimer = useRef(null);
  const guideRef = useRef(null);

  // States for branding video
  const [showBrandIntro, setShowBrandIntro] = useState(
    BRAND_INTRO_CONFIG.enabled &&
    (!source || source.type !== 'tv' || BRAND_INTRO_CONFIG.playOnTV)
  );
  const [brandTime, setBrandTime] = useState(0);
  const [canSkipBrand, setCanSkipBrand] = useState(false);
  const brandVideoRef = useRef(null);

  const showBrandIntroRef = useRef(showBrandIntro);
  useEffect(() => {
    showBrandIntroRef.current = showBrandIntro;
  }, [showBrandIntro]);

  const currentChannelIndex = useMemo(() => {
    if (!channelList?.length) return -1;
    return channelList.findIndex(ch => ch.id === localSource.id);
  }, [channelList, localSource.id]);

  const showChannelOverlay = useCallback((ch) => {
    setChannelInfo(ch);
    if (channelInfoTimer.current) clearTimeout(channelInfoTimer.current);
    channelInfoTimer.current = setTimeout(() => setChannelInfo(null), 3000);
  }, []);

  const switchToPrevChannel = useCallback(() => {
    if (!channelList?.length) return;
    const idx = currentChannelIndex <= 0 ? channelList.length - 1 : currentChannelIndex - 1;
    const ch = channelList[idx];
    showChannelOverlay(ch);
    onChannelChange?.(ch);
  }, [currentChannelIndex, channelList, onChannelChange, showChannelOverlay]);

  const switchToNextChannel = useCallback(() => {
    if (!channelList?.length) return;
    const idx = currentChannelIndex >= channelList.length - 1 ? 0 : currentChannelIndex + 1;
    const ch = channelList[idx];
    showChannelOverlay(ch);
    onChannelChange?.(ch);
  }, [currentChannelIndex, channelList, onChannelChange, showChannelOverlay]);

  const jumpToChannel = useCallback((ch) => {
    if (ch.id === localSource.id) return;
    setShowChannelGuide(false);
    showChannelOverlay(ch);
    onChannelChange?.(ch);
  }, [localSource.id, onChannelChange, showChannelOverlay]);

  // Series custom states
  const [activeEpisode, setActiveEpisode] = useState(source?.currentEpisode || null);
  const [showSeriesDrawer, setShowSeriesDrawer] = useState(false);
  const [selectedSeasonIndex, setSelectedSeasonIndex] = useState(0);
  const [countdownActive, setCountdownActive] = useState(false);
  const [countdownSeconds, setCountdownSeconds] = useState(10);
  const countdownIntervalRef = useRef(null);

  const findEpisodeIndicesForSource = (src, ep) => {
    if (!src?.seasons || !ep) return { seasonIdx: -1, episodeIdx: -1 };
    for (let s = 0; s < src.seasons.length; s++) {
      const idx = src.seasons[s].episodes.findIndex(e => e.id === ep.id || e.url === ep.url);
      if (idx !== -1) {
        return { seasonIdx: s, episodeIdx: idx };
      }
    }
    return { seasonIdx: -1, episodeIdx: -1 };
  };

  const findEpisodeIndices = (ep) => findEpisodeIndicesForSource(localSource, ep);

  const hasNextEpisode = () => {
    if (!localSource?.seasons || !activeEpisode) return false;
    const { seasonIdx, episodeIdx } = findEpisodeIndices(activeEpisode);
    if (seasonIdx === -1) return false;
    
    if (episodeIdx < localSource.seasons[seasonIdx].episodes.length - 1) {
      return true;
    }
    if (seasonIdx < localSource.seasons.length - 1) {
      return localSource.seasons[seasonIdx + 1]?.episodes && localSource.seasons[seasonIdx + 1].episodes.length > 0;
    }
    return false;
  };

  const hasPrevEpisode = () => {
    if (!localSource?.seasons || !activeEpisode) return false;
    const { seasonIdx, episodeIdx } = findEpisodeIndices(activeEpisode);
    if (seasonIdx === -1) return false;
    
    if (episodeIdx > 0) {
      return true;
    }
    if (seasonIdx > 0) {
      return localSource.seasons[seasonIdx - 1]?.episodes && localSource.seasons[seasonIdx - 1].episodes.length > 0;
    }
    return false;
  };

  const getNextEpisode = () => {
    if (!localSource?.seasons || !activeEpisode) return null;
    const { seasonIdx, episodeIdx } = findEpisodeIndices(activeEpisode);
    if (seasonIdx === -1) return null;
    
    if (episodeIdx < localSource.seasons[seasonIdx].episodes.length - 1) {
      return localSource.seasons[seasonIdx].episodes[episodeIdx + 1];
    }
    if (seasonIdx < localSource.seasons.length - 1) {
      return localSource.seasons[seasonIdx + 1]?.episodes?.[0] || null;
    }
    return null;
  };

  const getPrevEpisode = () => {
    if (!localSource?.seasons || !activeEpisode) return null;
    const { seasonIdx, episodeIdx } = findEpisodeIndices(activeEpisode);
    if (seasonIdx === -1) return null;
    
    if (episodeIdx > 0) {
      return localSource.seasons[seasonIdx].episodes[episodeIdx - 1];
    }
    if (seasonIdx > 0) {
      const prevSeasonEps = localSource.seasons[seasonIdx - 1]?.episodes;
      return prevSeasonEps && prevSeasonEps.length > 0 ? prevSeasonEps[prevSeasonEps.length - 1] : null;
    }
    return null;
  };

  const playEpisode = async (episode) => {
    if (!episode) return;
    
    setCountdownActive(false);
    setIsLoading(true);
    setErrorText(null);
    setActiveEpisode(episode);
    setShowSeriesDrawer(false);

    // Stop current video immediately so it doesn't play in background
    if (videoRef.current) {
      videoRef.current.pause();
      videoRef.current.src = "";
    }
    destroyPlayer();

    // Reset brand intro for next episode if configured
    if (BRAND_INTRO_CONFIG.enabled && BRAND_INTRO_CONFIG.playOnEpisodeChange) {
      setShowBrandIntro(true);
      setCanSkipBrand(false);
      setBrandTime(0);
    }
    
    const { seasonIdx } = findEpisodeIndicesForSource(localSource, episode);
    if (seasonIdx !== -1) {
      setSelectedSeasonIndex(seasonIdx);
    }
    
    const siteName = localSource.seriesInfo?.siteName || localSource.seriesInfo?.streams?.[0]?.name || '';
    
    try {
      console.log(`[VideoPlayer] Resolviendo nuevo episodio: ${episode.title} de ${siteName}`);
      const res = await fetch(`/api/series/episode/resolve?url=${encodeURIComponent(episode.url)}&siteName=${encodeURIComponent(siteName)}&lang=auto&debug=true`);
      const data = await res.json();
      
      if (data.success) {
        const updatedSource = {
          ...localSource,
          title: `${localSource.seriesInfo?.title} - ${episode.title}`,
          selectedLanguage: data.selectedLanguage,
          selectedServer: data.selectedServer,
          currentEpisode: episode,
          streams: [
            {
              name: data.selectedServer,
              url: data.stream.url,
              type: data.stream.type,
              headers: data.stream.headers || {},
              quality: data.stream.quality || "auto",
              resolver: data.stream.resolver || "direct"
            }
          ],
          attempts: data.attempts || []
        };
        
        setLocalSource(updatedSource);
        
        // Sincronizar el estado en el padre
        if (onNextEpisode) onNextEpisode(updatedSource);

        setActiveStreamIndex(0);
        setRetryCount(0);
        
        setTimeout(() => {
          setIsLoading(false);
          setPlayerKey(prev => prev + 1);
        }, 1000);
      } else {
        setIsLoading(false);
        setErrorText(data.error || "No hay fuentes reproducibles disponibles para este episodio.");
      }
    } catch (err) {
      console.error("[VideoPlayer] Error al cambiar episodio:", err);
      setIsLoading(false);
      setErrorText("Error de conexión al cargar el episodio. Intenta de nuevo.");
    }
  };

  const startCountdown = () => {
    setCountdownSeconds(10);
    setCountdownActive(true);
  };

  useEffect(() => {
    if (countdownActive) {
      countdownIntervalRef.current = setInterval(() => {
        setCountdownSeconds(prev => {
          if (prev <= 1) {
            clearInterval(countdownIntervalRef.current);
            const nextEp = getNextEpisode();
            if (nextEp) {
              playEpisode(nextEp);
            }
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } else {
      if (countdownIntervalRef.current) {
        clearInterval(countdownIntervalRef.current);
      }
    }

    return () => {
      if (countdownIntervalRef.current) {
        clearInterval(countdownIntervalRef.current);
      }
    };
  }, [countdownActive, activeEpisode]);

  const handleVideoEnded = () => {
    if (localSource?.isSeriesEpisode && hasNextEpisode()) {
      startCountdown();
    } else if (localSource?.type !== 'tv') {
      setShowEndScreen(true);
    }
  };

  useEffect(() => {
    setLocalSource(source);
    setRetryCount(0);
    setShowDebugInfo(false);
    if (source && source.available === false) {
      setErrorText(source.unavailableReason || "Esta fuente no está disponible.");
    } else {
      setErrorText(null);
    }

    if (source?.isSeriesEpisode) {
      setActiveEpisode(source.currentEpisode || null);
      setCountdownActive(false);
      setShowSeriesDrawer(false);
      
      const { seasonIdx } = findEpisodeIndicesForSource(source, source.currentEpisode);
      if (seasonIdx !== -1) {
        setSelectedSeasonIndex(seasonIdx);
      }
    } else {
      setActiveEpisode(null);
      setCountdownActive(false);
    }
  }, [source]);

  const videoRef = useRef(null);
  const playerRef = useRef(null);
  const hlsRef = useRef(null);
  const closeButtonRef = useRef(null);
  const controlsTimeoutRef = useRef(null);
  const backPressRef = useRef(0);
  const containerRef = useRef(null);

  const toggleFullscreen = useCallback(() => {
    const el = containerRef.current;
    if (!el) return;
    if (document.fullscreenElement) {
      document.exitFullscreen();
    } else {
      el.requestFullscreen();
    }
  }, []);

  const changeVolume = useCallback((newVol) => {
    const v = Math.max(0, Math.min(1, newVol));
    setVolume(v);
    if (videoRef.current) videoRef.current.volume = v;
    if (v > 0) setIsMuted(false);
  }, []);

  const toggleMute = useCallback(() => {
    if (videoRef.current) {
      videoRef.current.muted = !videoRef.current.muted;
      setIsMuted(videoRef.current.muted);
    }
  }, []);

  const changePlaybackRate = useCallback((rate) => {
    setPlaybackRate(rate);
    if (videoRef.current) videoRef.current.playbackRate = rate;
  }, []);

  const streams = localSource.streams || [];
  const activeRawStream = streams[activeStreamIndex] || null;
  const currentStream = activeRawStream;

  const resetControlsTimer = () => {
    setShowControls(true);
    if (controlsTimeoutRef.current) {
      clearTimeout(controlsTimeoutRef.current);
    }
    controlsTimeoutRef.current = setTimeout(() => {
      setShowControls(false);
    }, 5000);
  };

  useEffect(() => {
    resetControlsTimer();
    return () => {
      if (controlsTimeoutRef.current) {
        clearTimeout(controlsTimeoutRef.current);
      }
    };
  }, [currentStream]);

  useEffect(() => {
    const handleActivity = () => {
      resetControlsTimer();
    };

    window.addEventListener('mousemove', handleActivity);
    window.addEventListener('keydown', handleActivity);
    window.addEventListener('click', handleActivity);

    return () => {
      window.removeEventListener('mousemove', handleActivity);
      window.removeEventListener('keydown', handleActivity);
      window.removeEventListener('click', handleActivity);
    };
  }, []);

  useEffect(() => {
    if (showChannelGuide && guideRef.current) {
      const items = guideRef.current.querySelectorAll('.channel-guide-item');
      if (items[currentChannelIndex]) {
        const activeInGuide = document.activeElement?.closest('.channel-guide-item') && guideRef.current.contains(document.activeElement);
        if (!activeInGuide) {
          items[currentChannelIndex].focus();
          items[currentChannelIndex].scrollIntoView({ block: 'center' });
        }
      }
    }
  }, [showChannelGuide, currentChannelIndex]);

  // Auto-focus first episode when series drawer opens
  useEffect(() => {
    if (showSeriesDrawer) {
      setTimeout(() => {
        const firstEp = document.querySelector('.player-episode-item');
        if (firstEp) {
          firstEp.focus();
          firstEp.scrollIntoView({ block: 'nearest' });
        }
      }, 100);
    }
  }, [showSeriesDrawer]);

  useEffect(() => {
    const handleKeyDown = (e) => {
      const isTV = localSource.type === 'tv' && channelList?.length > 1;
      const activeEl = document.activeElement;

      // ── Brand Intro keys ────────────────────────────────────────
      if (showBrandIntro) {
        if (e.key === 'Escape' || e.key === 'Backspace') {
          e.preventDefault(); e.stopPropagation();
          onClose?.();
          return;
        }
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault(); e.stopPropagation();
          if (activeEl?.classList.contains('brand-skip-btn')) {
            finishBrandIntro();
          } else if (activeEl?.classList.contains('watch-close') || activeEl?.closest('.brand-intro-overlay .watch-close')) {
            onClose?.();
          }
          return;
        }
        // Let user navigate between skip button and close button inside the intro
        if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key)) {
          e.preventDefault(); e.stopPropagation();
          const introOverlay = document.querySelector('.brand-intro-overlay');
          if (!introOverlay) return;
          const focusables = Array.from(introOverlay.querySelectorAll('.focusable')).filter(el => {
            const rect = el.getBoundingClientRect();
            return rect.width > 0 && rect.height > 0 && !el.disabled;
          });
          if (focusables.length === 0) return;
          const curIdx = focusables.indexOf(activeEl);
          let nextIdx;
          if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
            nextIdx = curIdx < focusables.length - 1 ? curIdx + 1 : 0;
          } else {
            nextIdx = curIdx > 0 ? curIdx - 1 : focusables.length - 1;
          }
          focusables[nextIdx]?.focus();
          return;
        }
        return; // Block all other keys during brand intro
      }

      // ── Loader keys ─────────────────────────────────────────────
      if (isLoading) {
        if (e.key === 'Escape' || e.key === 'Backspace') {
          e.preventDefault(); e.stopPropagation();
          onClose?.();
          return;
        }
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault(); e.stopPropagation();
          onClose?.();
          return;
        }
        return; // Block other keys during load
      }

      // ── Series drawer navigation ────────────────────────────────
      const isInDrawer = activeEl?.closest('.player-series-drawer');
      if (localSource.isSeriesEpisode && isInDrawer) {
        if (e.key === 'Escape' || e.key === 'Backspace') {
          e.preventDefault(); e.stopPropagation();
          setShowSeriesDrawer(false);
          setTimeout(() => {
            const epBtn = document.querySelector('.custom-player-buttons-row .control-btn');
            if (epBtn) epBtn.focus();
          }, 0);
          return;
        }
        if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
          e.preventDefault(); e.stopPropagation();
          const items = guideRef.current?.querySelectorAll('.player-episode-item');
          if (!items?.length) return;
          const cur = Array.from(items).indexOf(activeEl);
          const dir = e.key === 'ArrowDown' ? 1 : -1;
          const next = (cur + dir + items.length) % items.length;
          items[next]?.focus();
          items[next]?.scrollIntoView({ block: 'nearest' });
          return;
        }
        if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
          e.preventDefault(); e.stopPropagation();
          const seasons = localSource.seasons;
          if (!seasons?.length) return;
          const dir = e.key === 'ArrowRight' ? 1 : -1;
          const next = (selectedSeasonIndex + dir + seasons.length) % seasons.length;
          setSelectedSeasonIndex(next);
          setTimeout(() => {
            const firstEp = document.querySelector('.player-episode-item');
            if (firstEp) firstEp.focus();
          }, 50);
          return;
        }
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault(); e.stopPropagation();
          if (activeEl?.classList.contains('player-episode-item')) {
            activeEl.click();
          }
          return;
        }
        // For any key not handled above, close drawer
        if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Enter', ' '].includes(e.key)) {
          return; // already handled or intentionally ignored
        }
      }

      // ── Arrow keys: navigate controls or show them ──────────────
      if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key)) {
        const isInGuide = activeEl?.closest('.channel-guide-panel');
        
        if (!showControls) {
          // Controls hidden → show them and focus first button
          e.preventDefault();
          e.stopPropagation();
          setShowControls(true);
          resetControlsTimer();
          const firstBtn = document.querySelector('.custom-player-buttons-row .control-btn');
          if (firstBtn) firstBtn.focus();
          return;
        }

        // Controls visible → let special elements handle left/right natively
        const isSpecial = activeEl?.classList?.contains?.('custom-player-progress-bar-wrapper')
                       || activeEl?.classList?.contains?.('volume-slider');
        if (isSpecial && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
          resetControlsTimer();
          return; // Let the element's own handler process it (seek / volume)
        }

        // Navigate between focusable elements
        e.preventDefault();
        e.stopPropagation();
        const player = document.querySelector('.watch-overlay');
        if (!player) return;
        const focusables = Array.from(player.querySelectorAll('.focusable')).filter(el => {
          const rect = el.getBoundingClientRect();
          return rect.width > 0 && rect.height > 0 && !el.disabled;
        });
        if (focusables.length === 0) return;
        const curIdx = focusables.indexOf(activeEl);
        let nextIdx;
        if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
          nextIdx = curIdx < focusables.length - 1 ? curIdx + 1 : 0;
        } else {
          nextIdx = curIdx > 0 ? curIdx - 1 : focusables.length - 1;
        }
        focusables[nextIdx]?.focus();
        resetControlsTimer();
        return;
      }

      // ── Channel guide mode ──────────────────────────────────────
      if (isTV && showChannelGuide) {
        if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
          e.preventDefault();
          e.stopPropagation();
          const items = guideRef.current?.querySelectorAll('.channel-guide-item');
          const focused = document.activeElement;
          if (items?.length) {
            let idx = Array.from(items).indexOf(focused);
            if (idx < 0) idx = currentChannelIndex;
            idx = e.key === 'ArrowUp'
              ? (idx - 1 + items.length) % items.length
              : (idx + 1) % items.length;
            items[idx]?.focus();
            items[idx]?.scrollIntoView({ block: 'nearest' });
          }
          return;
        }
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          e.stopPropagation();
          const focused = document.activeElement;
          if (focused?.classList.contains('channel-guide-item')) {
            const idx = parseInt(focused.dataset.index);
            if (!isNaN(idx) && channelList[idx]) jumpToChannel(channelList[idx]);
          }
          return;
        }
        if (e.key === 'Escape' || e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
          e.preventDefault();
          e.stopPropagation();
          setShowChannelGuide(false);
          return;
        }
      }

      // ── Escape / Backspace ─────────────────────────────────────
      if (e.key === 'Escape' || e.key === 'Backspace') {
        e.preventDefault();
        e.stopPropagation();
        resetControlsTimer();
        const now = Date.now();
        if (now - backPressRef.current < 2000) {
          onClose?.();
        } else {
          backPressRef.current = now;
        }
        return;
      }

      // ── Universal keyboard shortcuts ────────────────────────────
      if (e.key === ' ' || e.key === 'Space') {
        e.preventDefault(); e.stopPropagation(); resetControlsTimer();
        if (videoRef.current) {
          if (isPlaying) videoRef.current.pause();
          else videoRef.current.play().catch(() => {});
        }
        return;
      }
      if (e.key === 'f' || e.key === 'F') {
        e.preventDefault(); e.stopPropagation(); toggleFullscreen();
        return;
      }
      if (e.key === 'm' || e.key === 'M') {
        e.preventDefault(); e.stopPropagation(); toggleMute();
        return;
      }

      // ── Enter / Space on focused element ────────────────────────
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault(); e.stopPropagation(); resetControlsTimer();
        if (activeEl && activeEl !== document.body) {
          activeEl.click();
        }
        return;
      }

      // ── Any other key → show controls ─────────────────────────
      if (!showControls) {
        setShowControls(true); resetControlsTimer();
        e.preventDefault(); e.stopPropagation();
        const firstBtn = document.querySelector('.custom-player-buttons-row .control-btn');
        if (firstBtn) firstBtn.focus();
        return;
      }
      resetControlsTimer();
    };

    window.addEventListener('keydown', handleKeyDown, true);
    return () => {
      window.removeEventListener('keydown', handleKeyDown, true);
    };
  }, [showControls, localSource.type, channelList, showChannelGuide, currentChannelIndex, switchToPrevChannel, switchToNextChannel, jumpToChannel]);

  const handleStreamError = async (errorMsg) => {
    console.error("[VideoPlayer] Stream error:", errorMsg);

    // If it's an internal-resolver VOD movie/series, run backend retries with exclusions
    if (localSource.provider === 'internal-resolver' || localSource.provider === 'live-resolver') {
      if (retryCount < 3) {
        setIsLoading(true);
        setErrorText(`Servidor [${localSource.selectedServer}] falló. Buscando servidor alternativo (Intento ${retryCount + 1}/3)...`);
        
        try {
          const currentFailedServer = localSource.selectedServer;
          const failedServers = (localSource.attempts || [])
            .filter(att => att.status === 'FAILED')
            .map(att => att.sourceName);
            
          if (!failedServers.includes(currentFailedServer)) {
            failedServers.push(currentFailedServer);
          }

          let url, body, method;

          if (localSource.provider === 'internal-resolver') {
            url = `/api/movies/${localSource.id}/resolve?lang=auto`;
            failedServers.forEach(srv => {
              url += `&excludeServer=${encodeURIComponent(srv)}`;
            });
            method = 'GET';
            body = null;
          } else {
            url = `/api/live/resolve`;
            method = 'POST';
            body = JSON.stringify({
              streams: localSource.originalStreams || streams,
              type: localSource.type,
              excludeServer: failedServers
            });
          }

          console.log(`[VideoPlayer Retry] Intentando re-resolución en backend: ${url}`);
          const res = await fetch(url, {
            method,
            headers: body ? { 'Content-Type': 'application/json' } : undefined,
            body
          });
          const data = await res.json();

          if (data.success) {
            console.log(`[VideoPlayer Retry] Nuevo servidor resuelto: ${data.selectedServer}`);
            const updatedSource = {
              ...localSource,
              selectedLanguage: data.selectedLanguage,
              selectedServer: data.selectedServer,
              streams: [
                {
                  name: data.selectedServer,
                  url: data.stream.url,
                  type: data.stream.type,
                  headers: data.stream.headers || {},
                  quality: data.stream.quality || "auto",
                  resolver: data.stream.resolver || "direct"
                }
              ],
              attempts: data.attempts || []
            };

            setRetryCount(prev => prev + 1);
            setLocalSource(updatedSource);
            
            setTimeout(() => {
              setIsLoading(false);
              setErrorText(null);
              setPlayerKey(prev => prev + 1);
            }, 1500);
            return;
          }
        } catch (err) {
          console.error("[VideoPlayer Retry] Error obteniendo re-resolución:", err);
        }
      }
      setIsLoading(false);
      setErrorText("No encontramos una fuente disponible en este momento. Intenta más tarde.");
      return;
    }

    if (activeStreamIndex < streams.length - 1) {
      console.log(`[VideoPlayer] Auto-hopping to next stream index: ${activeStreamIndex + 1}`);
      setErrorText(`Servidor caído. Conectando al servidor alternativo...`);
      setTimeout(() => {
        setErrorText(null);
        setActiveStreamIndex(prev => prev + 1);
      }, 2000);
    } else {
      setErrorText(errorMsg || "La transmisión falló y no quedan más servidores alternativos.");
    }
  };

  const onVideoError = () => {
    destroyPlayer();
    handleStreamError("No se pudo sintonizar el canal en este servidor.");
  };

  const formatTime = (secs) => {
    if (isNaN(secs) || secs === Infinity) return '0:00';
    const hours = Math.floor(secs / 3600);
    const minutes = Math.floor((secs % 3600) / 60);
    const seconds = Math.floor(secs % 60);
    
    const paddedSeconds = seconds < 10 ? `0${seconds}` : seconds;
    
    if (hours > 0) {
      const paddedMinutes = minutes < 10 ? `0${minutes}` : minutes;
      return `${hours}:${paddedMinutes}:${paddedSeconds}`;
    }
    
    return `${minutes}:${paddedSeconds}`;
  };



  // Player initialization effect (Hls.js + Plyr)
  useEffect(() => {
    if (!currentStream || currentStream.resolver !== 'direct') {
      destroyPlayer();
      return;
    }

    const video = videoRef.current;
    if (!video) return;

    destroyPlayer();

    const isHls = currentStream.url.toLowerCase().includes('.m3u8');
    // Extract referer from headers if available
    const referer = currentStream.referer || (currentStream.headers && (currentStream.headers.referer || currentStream.headers.Referer));
    
    // Use proxy for all remote HTTP streams to bypass CORS, except localhost
    const streamUrl = currentStream.url.startsWith('http') && !currentStream.url.includes('localhost')
      ? `/api/proxy?url=${encodeURIComponent(currentStream.url)}${referer ? `&referer=${encodeURIComponent(referer)}` : ''}`
      : currentStream.url;

    if (isHls) {
      if (Hls.isSupported()) {
        const hls = new Hls({
          maxMaxBufferLength: 30,
          enableWorker: true,
          lowLatencyMode: true,
        });
        hlsRef.current = hls;
        hls.loadSource(streamUrl);
        hls.attachMedia(video);
        
        hls.on(Hls.Events.MANIFEST_PARSED, () => {
          playerRef.current = new Plyr(video, {
            controls: [], // Hide native controls
            autoplay: !showBrandIntroRef.current
          });
          if (!showBrandIntroRef.current) {
            playerRef.current.play().catch(() => {
              console.log("Autoplay blocked by browser");
            });
          }
        });

        hls.on(Hls.Events.AUDIO_TRACKS_UPDATED, (event, data) => {
          setAvailableAudioTracks(data.audioTracks || []);
          setActiveAudioTrack(hls.audioTrack);
        });

        hls.on(Hls.Events.AUDIO_TRACK_SWITCHED, (event, data) => {
          setActiveAudioTrack(data.id);
        });

        hls.on(Hls.Events.SUBTITLE_TRACKS_UPDATED, (event, data) => {
          setAvailableSubtitleTracks(data.subtitleTracks || []);
          setActiveSubtitleTrack(hls.subtitleTrack);
        });

        hls.on(Hls.Events.SUBTITLE_TRACK_SWITCH, (event, data) => {
          setActiveSubtitleTrack(data.id);
        });

        hls.on(Hls.Events.ERROR, function (event, data) {
          if (data.fatal) {
            switch (data.type) {
              case Hls.ErrorTypes.NETWORK_ERROR:
                console.log("fatal network error, try to recover");
                hls.startLoad();
                break;
              case Hls.ErrorTypes.MEDIA_ERROR:
                console.log("fatal media error, try to recover");
                hls.recoverMediaError();
                break;
              default:
                destroyPlayer();
                handleStreamError("Error de red fatal.");
                break;
            }
          }
        });
      } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
        video.src = streamUrl;
        playerRef.current = new Plyr(video, {
          controls: [],
          autoplay: !showBrandIntroRef.current
        });
        if (!showBrandIntroRef.current) {
          playerRef.current.play().catch(() => {});
        }
      }
    } else {
      video.src = streamUrl;
      playerRef.current = new Plyr(video, {
        controls: [], // Hide native controls
        autoplay: !showBrandIntroRef.current
      });
      if (!showBrandIntroRef.current) {
        playerRef.current.play().catch(() => {
          console.log("Autoplay blocked by browser");
        });
      }
    }

    return () => {
      destroyPlayer();
    };
  }, [activeStreamIndex, currentStream, playerKey]);

  // Video event listeners (timeupdate, play, pause, etc.)
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const onTimeUpdate = () => setCurrentTime(video.currentTime);
    const onPlay = () => setIsPlaying(true);
    const onPause = () => setIsPlaying(false);
    const onLoadedMeta = () => setDuration(video.duration || 0);
    const onEnded = () => setIsPlaying(false);

    video.addEventListener('timeupdate', onTimeUpdate);
    video.addEventListener('play', onPlay);
    video.addEventListener('pause', onPause);
    video.addEventListener('loadedmetadata', onLoadedMeta);
    video.addEventListener('ended', onEnded);

    return () => {
      video.removeEventListener('timeupdate', onTimeUpdate);
      video.removeEventListener('play', onPlay);
      video.removeEventListener('pause', onPause);
      video.removeEventListener('loadedmetadata', onLoadedMeta);
      video.removeEventListener('ended', onEnded);
    };
  }, [currentStream, playerKey]);

  // Helper functions for brand intro
  const handleBrandTimeUpdate = () => {
    if (!brandVideoRef.current) return;
    const cur = brandVideoRef.current.currentTime;
    setBrandTime(cur);
    if (BRAND_INTRO_CONFIG.skipDelay !== null && cur >= BRAND_INTRO_CONFIG.skipDelay) {
      setCanSkipBrand(true);
    }
  };

  const handleBrandIntroEnded = () => {
    console.log("[VideoPlayer] Brand intro ended naturally.");
    finishBrandIntro();
  };

  const handleBrandIntroError = (e) => {
    console.warn("[VideoPlayer] Brand intro video failed to load or play (might be missing file). Skipping intro gracefully.", e);
    finishBrandIntro();
  };

  const finishBrandIntro = () => {
    setShowBrandIntro(false);
  };

  // Play main video when brand intro finishes/is skipped
  useEffect(() => {
    if (!showBrandIntro && videoRef.current) {
      console.log("[VideoPlayer] Brand intro finished/skipped. Playing main video.");
      if (playerRef.current) {
        playerRef.current.play().catch(err => {
          console.log("[VideoPlayer] Error playing via Plyr, trying native video:", err);
          videoRef.current?.play().catch(e => console.log("[VideoPlayer] Native play failed:", e));
        });
      } else {
        videoRef.current.play().catch(e => console.log("[VideoPlayer] Native play failed:", e));
      }
    }
  }, [showBrandIntro]);

  // Set focus on close button or skip button on mount/state change
  useEffect(() => {
    if (showBrandIntro) {
      setTimeout(() => {
        const skipBtn = document.querySelector('.brand-skip-btn');
        if (skipBtn) {
          skipBtn.focus();
        } else {
          const introCloseBtn = document.querySelector('.brand-intro-overlay .watch-close');
          if (introCloseBtn) introCloseBtn.focus();
        }
      }, 100);
    } else if (!isLoading && !errorText && closeButtonRef.current) {
      closeButtonRef.current.focus();
    }
  }, [showBrandIntro, canSkipBrand, isLoading, errorText, currentStream]);

  const destroyPlayer = () => {
    if (playerRef.current) {
      playerRef.current.destroy();
      playerRef.current = null;
    }
    if (hlsRef.current) {
      hlsRef.current.destroy();
      hlsRef.current = null;
    }
  };

  const handleSourceChange = (index) => {
    setActiveStreamIndex(index);
  };

  const getEmbedUrl = () => {
    if (!currentStream) return '';
    
    // If the stream already has an iframe URL, use it directly
    if (currentStream.resolver === 'iframe') {
      return currentStream.url;
    }

    if (currentStream.resolver === 'vidsrc') {
      const tmdbId = localSource.tmdbId;
      if (!tmdbId) return '';
      const type = localSource.type === 'series' ? 'tv' : 'movie';
      return `https://vidsrc.to/embed/${type}/${tmdbId}`;
    }

    return currentStream.url;
  };

  if (errorText) {
    return (
      <div className="watch-overlay" style={{ justifyContent: 'center', alignItems: 'center' }}>
        <div className="watch-header">
          <div className="watch-title">{localSource.title}</div>
          <button ref={closeButtonRef} className="watch-close focusable" tabIndex={0} onClick={onClose} title="Cerrar Reproductor">
            <X size={20} />
          </button>
        </div>
        <div style={{ textAlign: 'center', maxWidth: '450px', padding: '24px' }} className="glass-panel form-card">
          <h3 style={{ fontSize: '1.3rem', fontWeight: 700, color: 'var(--accent)', marginBottom: '12px' }}>Error de Conexión</h3>
          <p className="text-secondary" style={{ marginBottom: '20px', fontSize: '0.95rem' }}>{errorText}</p>
          <button className="btn btn-secondary focusable" tabIndex={0} onClick={onClose}>Cerrar</button>
        </div>
      </div>
    );
  }

  return (
    <div ref={containerRef} className="watch-overlay">
      {/* Top Header overlay */}
      <div className={`watch-header-overlay ${showControls ? 'visible' : 'hidden'}`}>
        <div className="watch-title-area">
          <div className="watch-title">
            {localSource.isSeriesEpisode ? localSource.seriesInfo?.title : localSource.title}
          </div>
          <div className="watch-subtitle">
            {localSource.isSeriesEpisode ? (
              <>
                {localSource.currentEpisode ? (
                  `T${findEpisodeIndices(localSource.currentEpisode).seasonIdx + 1}:E${localSource.currentEpisode.episodeNum || 1} - ${localSource.currentEpisode.title}`
                ) : (
                  'Serie de TV'
                )}
              </>
            ) : (
              localSource.type === 'tv' ? 'Canal en Vivo' : 'Película'
            )}
            {streams.length > 1 && ` • Servidor ${activeStreamIndex + 1} de ${streams.length}`}
          </div>
        </div>
        <button ref={closeButtonRef} className="watch-close focusable" tabIndex={0} onClick={onClose} title="Cerrar Reproductor">
          <X size={20} />
        </button>
      </div>

      {/* ── Video element ─────────────────────────────────────────── */}
      <div className="player-wrapper">
        {currentStream && currentStream.resolver !== 'direct' ? (
          !showBrandIntro && (
            <iframe
              src={getEmbedUrl()}
              allow="autoplay; fullscreen"
              allowFullScreen
              style={{ width: '100%', height: '100%', border: 'none' }}
            />
          )
        ) : (
          <video
            ref={videoRef}
            className="plyr-video"
            playsInline
            style={{ width: '100%', height: '100%', objectFit: 'contain' }}
          />
        )}
      </div>

      {/* ── Bottom controls overlay ──────────────────────────────────── */}
      <div className={`custom-player-overlay ${showControls ? 'visible' : 'hidden'}`}>
        <div className="custom-player-bottom-bar">

          {/* Progress bar (solo películas/series) */}
          {localSource.type !== 'tv' && (
            <div className="custom-player-progress-container">
              <span className="custom-player-time">{formatTime(currentTime)}</span>
              <div
                className="custom-player-progress-bar-wrapper focusable"
                tabIndex={0}
                onClick={(e) => {
                  const rect = e.currentTarget.getBoundingClientRect();
                  const pct = (e.clientX - rect.left) / rect.width;
                  const t = pct * (duration || 0);
                  if (videoRef.current) videoRef.current.currentTime = t;
                  setCurrentTime(t);
                }}
                onKeyDown={(e) => {
                  if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
                    e.preventDefault();
                    e.stopPropagation();
                    const step = duration ? duration * 0.02 : 5;
                    const dir = e.key === 'ArrowLeft' ? -1 : 1;
                    const t = Math.max(0, Math.min(duration || 0, (videoRef.current?.currentTime || 0) + step * dir));
                    if (videoRef.current) videoRef.current.currentTime = t;
                    setCurrentTime(t);
                  }
                }}
              >
                <div className="custom-player-progress-bar-fill" style={{ width: `${duration ? (currentTime / duration) * 100 : 0}%` }} />
              </div>
              <span className="custom-player-time">{formatTime(duration)}</span>
            </div>
          )}

          <div className="custom-player-buttons-row">
            {/* Play/Pause */}
            <button className="control-btn focusable" tabIndex={0} onClick={() => {
              if (!videoRef.current) return;
              if (isPlaying) videoRef.current.pause();
              else videoRef.current.play().catch(() => {});
            }}>
              {isPlaying ? <Pause size={22} /> : <Play size={22} />}
            </button>

            {/* Seek backward 10s (no TV) */}
            {localSource.type !== 'tv' && (
              <button className="control-btn focusable" tabIndex={0} onClick={() => {
                if (videoRef.current) videoRef.current.currentTime = Math.max(0, (videoRef.current.currentTime || 0) - 10);
              }}>
                <RotateCcw size={18} />
              </button>
            )}

            {/* Seek forward 10s (no TV) */}
            {localSource.type !== 'tv' && (
              <button className="control-btn focusable" tabIndex={0} onClick={() => {
                if (videoRef.current && duration) videoRef.current.currentTime = Math.min(duration, (videoRef.current.currentTime || 0) + 10);
              }}>
                <RotateCw size={18} />
              </button>
            )}

            {/* Series episode navigation */}
            {localSource.isSeriesEpisode && (
              <>
                <span className="custom-player-time" style={{ color: 'var(--text-muted)', fontWeight: 600, fontSize: '0.8rem' }}>
                  T{findEpisodeIndices(activeEpisode).seasonIdx + 1}:E{activeEpisode?.episodeNum || 1}
                </span>
                <button className="control-btn focusable" tabIndex={0} onClick={() => { const p = getPrevEpisode(); if (p) playEpisode(p); }}>
                  <SkipBack size={18} />
                </button>
                <button className="control-btn focusable" tabIndex={0} onClick={() => setShowSeriesDrawer(true)} title="Lista de episodios">
                  <List size={18} />
                </button>
                <button className="control-btn focusable" tabIndex={0} onClick={() => { const n = getNextEpisode(); if (n) playEpisode(n); }}>
                  <SkipForward size={18} />
                </button>
              </>
            )}

            {/* TV channel navigation */}
            {localSource.type === 'tv' && channelList?.length > 1 && (
              <>
                <span className="custom-player-time" style={{ color: 'var(--text-muted)', fontWeight: 600 }}>Canales</span>
                <button className="control-btn focusable" tabIndex={0} onClick={switchToPrevChannel}>
                  <SkipBack size={20} />
                </button>
                <button className="control-btn focusable" tabIndex={0} onClick={switchToNextChannel}>
                  <SkipForward size={20} />
                </button>
                <button className="control-btn focusable" tabIndex={0} onClick={() => setShowChannelGuide(true)}>
                  <List size={20} />
                </button>
              </>
            )}

            <div style={{ flexGrow: 1 }} />

            {/* Volume (no TV) */}
            {localSource.type !== 'tv' && (
              <div className="volume-slider-wrapper">
                <button className="control-btn focusable" tabIndex={0} onClick={toggleMute}>
                  {isMuted ? <VolumeX size={20} /> : <Volume2 size={20} />}
                </button>
                <input
                  type="range"
                  className="volume-slider focusable"
                  min={0}
                  max={1}
                  step={0.05}
                  value={isMuted ? 0 : volume}
                  onChange={(e) => changeVolume(parseFloat(e.target.value))}
                  tabIndex={0}
                />
              </div>
            )}

            {/* Playback speed (no TV) */}
            {localSource.type !== 'tv' && (
              <button className="control-btn focusable" tabIndex={0} onClick={() => {
                const rates = [0.5, 0.75, 1, 1.25, 1.5, 2];
                const idx = rates.indexOf(playbackRate);
                const next = rates[(idx + 1) % rates.length];
                changePlaybackRate(next);
              }}>
                {playbackRate}x
              </button>
            )}

            {/* Fullscreen */}
            <button className="control-btn focusable" tabIndex={0} onClick={toggleFullscreen}>
              {document.fullscreenElement ? <Minimize2 size={20} /> : <Maximize2 size={20} />}
            </button>
          </div>
        </div>
      </div>

      {/* ── Series episode drawer ────────────────────────────────────── */}
      {localSource.isSeriesEpisode && localSource.seasons && (
        <div ref={guideRef} className={`player-series-drawer ${showSeriesDrawer ? 'visible' : 'hidden'}`}>
          <div className="player-drawer-header">
            <h3 className="player-drawer-title">Episodios</h3>
            <button className="player-drawer-close focusable" tabIndex={0} onClick={() => setShowSeriesDrawer(false)}>
              <X size={22} />
            </button>
          </div>

          <div className="player-drawer-content">
            {/* Season selector */}
            <div className="player-season-select-wrapper">
              <select
                className="player-season-select focusable"
                value={selectedSeasonIndex}
                onChange={(e) => {
                  const idx = parseInt(e.target.value);
                  setSelectedSeasonIndex(idx);
                  setTimeout(() => {
                    const firstEp = document.querySelector('.player-episode-item');
                    if (firstEp) firstEp.focus();
                  }, 50);
                }}
                tabIndex={0}
              >
                {localSource.seasons.map((season, i) => (
                  <option key={i} value={i}>{season.title}</option>
                ))}
              </select>
            </div>

            {/* Episode list */}
            <div className="player-episodes-list">
              {localSource.seasons[selectedSeasonIndex]?.episodes.map((ep, i) => {
                const isActive = activeEpisode && (ep.id === activeEpisode.id || ep.url === activeEpisode.url);
                return (
                  <button
                    key={i}
                    className={`player-episode-item focusable ${isActive ? 'active' : ''}`}
                    tabIndex={0}
                    onClick={() => playEpisode(ep)}
                  >
                    <span className="player-episode-item-number">{ep.episodeNum}</span>
                    <span className="player-episode-item-title">{ep.title}</span>
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* Admin Debug Panel */}
      {showDebugInfo && localSource.attempts && localSource.attempts.length > 0 && (
        <div style={{
          position: 'absolute',
          top: '80px',
          left: '20px',
          zIndex: 10000,
          background: 'rgba(20, 26, 50, 0.95)',
          padding: '16px',
          borderRadius: '8px',
          width: '320px',
          border: '1px solid var(--primary-light)',
          boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
          backdropFilter: 'blur(10px)',
          textAlign: 'left'
        }}>
          <h4 style={{ fontSize: '0.95rem', fontWeight: 600, color: '#fff', marginBottom: '8px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>Historial de Resolución</span>
            <span style={{ fontSize: '0.75rem', color: 'var(--primary-light)' }}>{localSource.selectedLanguage}</span>
          </h4>
          <p style={{ fontSize: '0.8rem', color: 'rgba(255,255,255,0.7)', marginBottom: '12px' }}>
            Servidor actual: <strong style={{ color: '#4caf50' }}>{localSource.selectedServer}</strong>
          </p>
          <div style={{ maxHeight: '180px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '6px' }}>
            {localSource.attempts.map((att, idx) => (
              <div key={idx} style={{
                fontSize: '0.75rem',
                padding: '6px',
                borderRadius: '4px',
                background: att.status === 'SUCCESS' ? 'rgba(76, 175, 80, 0.15)' : 'rgba(244, 67, 54, 0.15)',
                borderLeft: `3px solid ${att.status === 'SUCCESS' ? '#4caf50' : '#f44336'}`
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 600, color: '#fff' }}>
                  <span>{att.sourceName}</span>
                  <span style={{ color: att.status === 'SUCCESS' ? '#4caf50' : '#f44336' }}>{att.status}</span>
                </div>
                <div style={{ fontSize: '0.7rem', color: 'rgba(255,255,255,0.5)', marginTop: '2px' }}>
                  Idioma: {att.language} | Motivo: {att.reason}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* End screen for movies */}
      {showEndScreen && localSource.type !== 'tv' && !localSource.isSeriesEpisode && (
        <div className="player-next-countdown">
          <h4 className="player-countdown-label" style={{ fontSize: '1.4rem', color: '#ffcc00' }}>
            Fin de la reproducción
          </h4>
          <p style={{ color: 'rgba(255,255,255,0.6)', marginBottom: '20px' }}>
            {localSource.title}
          </p>
          <div className="player-countdown-actions">
            <button 
              className="btn btn-secondary focusable"
              onClick={() => {
                setShowEndScreen(false);
                if (videoRef.current) {
                  videoRef.current.currentTime = 0;
                  videoRef.current.play().catch(() => {});
                }
              }}
            >
              Reproducir de nuevo
            </button>
            <button 
              className="btn btn-primary focusable"
              onClick={onClose}
            >
              Cerrar
            </button>
          </div>
        </div>
      )}

      {/* Series countdown overlay */}
      {countdownActive && getNextEpisode() && (
        <div className="player-next-countdown">
          <h4 className="player-countdown-label">Siguiente episodio en</h4>
          <div className="player-countdown-circle-wrapper">
            <span className="player-countdown-number">{countdownSeconds}</span>
          </div>
          <p className="player-countdown-ep-title">
            {getNextEpisode()?.title}
          </p>
          <div className="player-countdown-actions">
            <button 
              className="btn btn-secondary focusable"
              onClick={() => setCountdownActive(false)}
            >
              Cancelar
            </button>
            <button 
              className="btn btn-primary focusable"
              onClick={() => {
                const nextEp = getNextEpisode();
                if (nextEp) playEpisode(nextEp);
              }}
            >
              Reproducir Ya
            </button>
          </div>
        </div>
      )}

      {/* ── Loader overlay ─────────────────────────────────────────── */}
      {isLoading && (
        <div style={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: '100%',
          height: '100%',
          background: '#000',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          alignItems: 'center',
          zIndex: 1008
        }}>
          <div className="watch-header" style={{ position: 'absolute', top: 0, left: 0, width: '100%', pointerEvents: 'none' }}>
            <div className="watch-title">{localSource.title}</div>
            <button ref={closeButtonRef} className="watch-close focusable" tabIndex={0} onClick={onClose} title="Cerrar Reproductor" style={{ pointerEvents: 'auto' }}>
              <X size={20} />
            </button>
          </div>
          <div style={{ textAlign: 'center' }}>
            <RefreshCw className="animate-spin text-muted mx-auto mb-4" size={48} style={{ color: 'var(--primary-light)', animation: 'spin 2s linear infinite' }} />
            <h3 style={{ fontSize: '1.5rem', fontWeight: 600, color: '#fff', marginBottom: '8px' }}>Sintonizando transmisión...</h3>
            <p className="text-secondary">Buscando el servidor más estable y omitiendo anuncios de origen...</p>
          </div>
        </div>
      )}

      {/* ── Brand Intro overlay ─────────────────────────────────────── */}
      {showBrandIntro && (
        <div className="brand-intro-overlay">
          <div className="watch-header" style={{ position: 'absolute', top: 0, left: 0, width: '100%', pointerEvents: 'none' }}>
            <div className="watch-title">{localSource.title}</div>
            <button className="watch-close focusable" tabIndex={0} onClick={onClose} title="Cerrar Reproductor" style={{ pointerEvents: 'auto' }}>
              <X size={20} />
            </button>
          </div>
          
          <video
            ref={brandVideoRef}
            src={BRAND_INTRO_CONFIG.videoUrl}
            autoPlay
            playsInline
            onEnded={handleBrandIntroEnded}
            onError={handleBrandIntroError}
            onTimeUpdate={handleBrandTimeUpdate}
            className="brand-intro-video"
          />

          {canSkipBrand && (
            <button 
              className="brand-skip-btn focusable"
              tabIndex={0}
              onClick={finishBrandIntro}
            >
              Omitir
            </button>
          )}
        </div>
      )}
    </div>
  );
}
