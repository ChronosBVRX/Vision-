import React, { useEffect, useRef, useState, useMemo, useCallback } from 'react';
import Plyr from 'plyr';
import Hls from 'hls.js';
import { X, Play, Pause, RefreshCw, Layers, RotateCcw, RotateCw, SkipForward, SkipBack, List, Globe as Globe2, Subtitles, Volume2, VolumeX, Maximize2, Minimize2, FastForward, Search, Tv } from 'lucide-react';
import 'plyr/dist/plyr.css';
import LoadingScreen from './LoadingScreen';

// BRANDING PRE-ROLL CONFIGURATION
const BRAND_INTRO_CONFIG = {
  enabled: true,                  // Enable/disable the brand intro video
  videoUrl: '/brand-intro.mp4',   // Local path (frontend/public/) or remote URL
  skipDelay: 2,                   // Seconds before showing the Skip button (0 for immediate, null for unskippable)
  playOnTV: false,                // Show on Live TV channels?
  playOnEpisodeChange: false      // Show on every episode change?
};

function getNormalizedTVCategory(channel) {
  if (!channel) return 'Variedades / General';
  
  const title = (channel.title || '').toLowerCase();
  const titleClean = title.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
  
  let cat = (channel.category || '').replace(/^(Planeta Play - |Pluto TV - |Canales - )/i, '').trim();
  const catClean = cat.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "");

  // 1. Anime
  if (
    titleClean.includes('anime') || 
    titleClean.includes('animax') || 
    titleClean.includes('locomotion') || 
    titleClean.includes('otaku') ||
    catClean.includes('anime')
  ) {
    return 'Anime';
  }

  // 2. Deportes
  if (
    titleClean.includes('espn') || 
    titleClean.includes('fox sports') || 
    titleClean.includes('sportv') ||
    titleClean.includes('deportes') || 
    titleClean.includes('sports') || 
    titleClean.includes('tudn') ||
    titleClean.includes('win sports') || 
    titleClean.includes('golf') || 
    titleClean.includes('f1') ||
    titleClean.includes('ufc') || 
    titleClean.includes('nba') || 
    titleClean.includes('nascar') ||
    titleClean.includes('bein') || 
    titleClean.includes('tyc') || 
    titleClean.includes('arena') ||
    titleClean.includes('laliga') || 
    titleClean.includes('futbol') || 
    titleClean.includes('garage') ||
    titleClean.includes('motorvision') || 
    titleClean.includes('dazn') ||
    titleClean.includes('wwe') ||
    titleClean.includes('lucha libre') ||
    titleClean.includes('mma') ||
    titleClean.includes('combat') ||
    titleClean.includes('eurosport') ||
    titleClean.includes('directv sports') ||
    titleClean.includes('dgo') ||
    titleClean.includes('red bull') ||
    titleClean.includes('redbull') ||
    catClean.includes('deporte') ||
    catClean.includes('sport')
  ) {
    return 'Deportes';
  }

  // 3. Kids / Infantil
  if (
    titleClean.includes('disney') || 
    titleClean.includes('cartoon') || 
    titleClean.includes('nickelodeon') ||
    titleClean.includes('nick ') || 
    titleClean.includes('discovery kids') || 
    titleClean.includes('boing') ||
    titleClean.includes('infantil') || 
    titleClean.includes('kids') || 
    titleClean.includes('baby') ||
    titleClean.includes('toonline') || 
    titleClean.includes('laika') ||
    titleClean.includes('boomerang') ||
    titleClean.includes('semillitas') ||
    titleClean.includes('babyfirst') ||
    titleClean.includes('doraemon') ||
    titleClean.includes('peppa') ||
    titleClean.includes('clan') ||
    catClean.includes('kids') ||
    catClean.includes('infantil')
  ) {
    return 'Kids / Infantil';
  }

  // 4. Cine & Series
  if (
    titleClean.includes('hbo') || 
    titleClean.includes('cine') || 
    titleClean.includes('pelicula') ||
    titleClean.includes('movie') || 
    titleClean.includes('tnt') || 
    titleClean.includes('space') ||
    titleClean.includes('amc') || 
    titleClean.includes('axn') || 
    titleClean.includes('fx') ||
    titleClean.includes('fox channel') || 
    titleClean.includes('star channel') || 
    titleClean.includes('cinecanal') ||
    titleClean.includes('golden') || 
    titleClean.includes('multipremier') || 
    titleClean.includes('studio universal') ||
    titleClean.includes('paramount') || 
    titleClean.includes('h&h') || 
    titleClean.includes('universal tv') ||
    titleClean.includes('cinemax') ||
    titleClean.includes('warner') ||
    titleClean.includes('sony') ||
    titleClean.includes('comedy central') ||
    titleClean.includes('syfy') ||
    titleClean.includes('mgm') ||
    titleClean.includes('film') ||
    catClean.includes('cine') ||
    catClean.includes('series') ||
    catClean.includes('pelicula')
  ) {
    return 'Cine & Series';
  }

  // 5. Noticias
  if (
    titleClean.includes('cnn') || 
    titleClean.includes('noticias') || 
    titleClean.includes('news') ||
    titleClean.includes('24 horas') || 
    titleClean.includes('rt') || 
    titleClean.includes('telesur') ||
    titleClean.includes('dw') || 
    titleClean.includes('prensa') || 
    titleClean.includes('la nacion') ||
    titleClean.includes('todo noticias') ||
    titleClean.includes('euronews') ||
    titleClean.includes('bloomberg') ||
    titleClean.includes('c5n') ||
    titleClean.includes('a24') ||
    titleClean.includes('tn') ||
    titleClean.includes('canal 26') ||
    titleClean.includes('milenio') ||
    titleClean.includes('forotv') ||
    titleClean.includes('foro tv') ||
    titleClean.includes('ntn24') ||
    catClean.includes('noticias') ||
    catClean.includes('news')
  ) {
    return 'Noticias';
  }

  // 6. Documentales
  if (
    titleClean.includes('discovery') || 
    titleClean.includes('national geographic') ||
    titleClean.includes('nat geo') || 
    titleClean.includes('natgeo') ||
    titleClean.includes('history') || 
    titleClean.includes('animal planet') ||
    titleClean.includes('documental') || 
    titleClean.includes('biography') || 
    titleClean.includes('investigation') ||
    titleClean.includes('smithsonian') ||
    titleClean.includes('odisea') ||
    titleClean.includes('viajar') ||
    titleClean.includes('ciencia') ||
    titleClean.includes('nasa') ||
    titleClean.includes('wild') ||
    catClean.includes('documental') ||
    catClean.includes('ciencia') ||
    catClean.includes('investigacion')
  ) {
    return 'Documentales';
  }

  // 7. Música
  if (
    titleClean.includes('mtv') || 
    titleClean.includes('musica') || 
    titleClean.includes('music') ||
    titleClean.includes('viva') || 
    titleClean.includes('vh1') || 
    titleClean.includes('htv') ||
    titleClean.includes('telehit') ||
    titleClean.includes('k-pop') ||
    titleClean.includes('kpop') ||
    titleClean.includes('concert') ||
    catClean.includes('musica') ||
    catClean.includes('music')
  ) {
    return 'Música';
  }

  if (
    catClean === 'canales en vivo' || 
    catClean === 'general' || 
    catClean === 'importado' || 
    !cat
  ) {
    return 'Variedades / General';
  }

  return cat;
}

export default function VideoPlayer({ source, onClose, onNext, onNextEpisode, onPrevEpisode, channelList, onChannelChange, onSourceChange }) {
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

  // Live TV custom states
  const isLive = useMemo(() => {
    return localSource?.type === 'tv' || localSource?.type === 'sports' || localSource?.isSports;
  }, [localSource]);

  const [showZappingBanner, setShowZappingBanner] = useState(false);
  const zappingTimer = useRef(null);
  const [guideSearchQuery, setGuideSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('Todos');
  const [showAudioPopup, setShowAudioPopup] = useState(false);
  const [showSubtitlePopup, setShowSubtitlePopup] = useState(false);
  const [showServerPopup, setShowServerPopup] = useState(false);
  const [showVODServerPopup, setShowVODServerPopup] = useState(false);

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

    if (source?.selectedStreamIndex !== undefined) {
      setActiveStreamIndex(source.selectedStreamIndex);
    } else {
      setActiveStreamIndex(0);
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
  const dashRef = useRef(null);
  const closeButtonRef = useRef(null);
  const controlsTimeoutRef = useRef(null);
  const backPressRef = useRef(0);
  const containerRef = useRef(null);
  // Keep refs in sync with states so keydown handler (closure) always reads fresh values
  const showControlsRef = useRef(true);
  useEffect(() => { showControlsRef.current = showControls; }, [showControls]);
  // isLoading ref — critical: the keydown handler is registered once (closure), 
  // so without this ref it would keep seeing the initial isLoading=false value
  // and for movies after brand intro it would incorrectly block key handling.
  const isLoadingRef = useRef(isLoading);
  useEffect(() => { isLoadingRef.current = isLoading; }, [isLoading]);
  // Detect Smart TV environment — must be declared before KEY_THROTTLE_MS uses it
  const isSmartTV = typeof window !== 'undefined' && window.isSmartTV === true;
  // Throttle ref for Smart TV — prevents rapid key repeat from queuing expensive ops
  const lastKeyTimeRef = useRef(0);
  const KEY_THROTTLE_MS = isSmartTV ? 120 : 0;
  const CONTROLS_TIMEOUT = isSmartTV ? 15000 : 5000;

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
    showControlsRef.current = true;
    if (controlsTimeoutRef.current) {
      clearTimeout(controlsTimeoutRef.current);
    }
    controlsTimeoutRef.current = setTimeout(() => {
      setShowControls(false);
      showControlsRef.current = false;
    }, CONTROLS_TIMEOUT);
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

  // Trigger zapping banner on channel change
  useEffect(() => {
    if (isLive && localSource) {
      setShowZappingBanner(true);
      if (zappingTimer.current) clearTimeout(zappingTimer.current);
      zappingTimer.current = setTimeout(() => {
        setShowZappingBanner(false);
      }, 3500);
    }
    return () => {
      if (zappingTimer.current) clearTimeout(zappingTimer.current);
    };
  }, [localSource?.id, isLive]);

  // Auto-close popups when controls are hidden
  useEffect(() => {
    if (!showControls) {
      setShowAudioPopup(false);
      setShowSubtitlePopup(false);
      setShowServerPopup(false);
    }
  }, [showControls]);

  // EPG list categories
  const categories = useMemo(() => {
    if (!channelList?.length) return ['Todos'];
    const cats = new Set();
    channelList.forEach(ch => {
      cats.add(getNormalizedTVCategory(ch));
    });
    return ['Todos', ...Array.from(cats).sort()];
  }, [channelList]);

  // EPG list filtering
  const filteredChannels = useMemo(() => {
    if (!channelList?.length) return [];
    return channelList.filter(ch => {
      const titleMatch = (ch.title || '').toLowerCase().includes(guideSearchQuery.toLowerCase());
      const catMatch = selectedCategory === 'Todos' || getNormalizedTVCategory(ch) === selectedCategory;
      return titleMatch && catMatch;
    });
  }, [channelList, guideSearchQuery, selectedCategory]);

  useEffect(() => {
    const handleKeyDown = (e) => {
      // ── Smart TV throttle: skip rapid key repeats ────────────────
      if (isSmartTV && KEY_THROTTLE_MS > 0) {
        const now = performance.now();
        if (now - lastKeyTimeRef.current < KEY_THROTTLE_MS) {
          e.preventDefault();
          e.stopPropagation();
          return;
        }
        lastKeyTimeRef.current = now;
      }

      const isTV = isLive && channelList?.length > 1;
      const activeEl = document.activeElement;

      // ── Brand Intro keys ────────────────────────────────────────
      // Use ref to avoid stale closure — handler is registered once but showBrandIntro changes
      if (showBrandIntroRef.current) {
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
      // Use ref to avoid stale closure — isLoading changes after streams resolve
      if (isLoadingRef.current) {
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

      // ── Audio/Subtitle/Server popups navigation ─────────────────
      const isInPopup = activeEl?.closest('.player-popup-menu');
      if (isInPopup) {
        if (e.key === 'Escape' || e.key === 'Backspace') {
          e.preventDefault(); e.stopPropagation();
          setShowAudioPopup(false);
          setShowSubtitlePopup(false);
          setShowServerPopup(false);
          // Focus the button that opened it
          setTimeout(() => {
            const btn = document.querySelector('.custom-player-buttons-row .control-btn');
            if (btn) btn.focus();
          }, 0);
          return;
        }
        if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
          e.preventDefault(); e.stopPropagation();
          const items = Array.from(document.querySelectorAll('.player-popup-item'));
          const cur = items.indexOf(activeEl);
          const dir = e.key === 'ArrowDown' ? 1 : -1;
          const next = (cur + dir + items.length) % items.length;
          items[next]?.focus();
          return;
        }
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault(); e.stopPropagation();
          activeEl.click();
          return;
        }
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
        // Use ref to avoid stale closure — showControlsRef always has fresh value
        const controlsCurrentlyVisible = showControlsRef.current;
        
        if (!controlsCurrentlyVisible) {
          if (isTV) {
            if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
              e.preventDefault();
              e.stopPropagation();
              if (e.key === 'ArrowUp') {
                switchToPrevChannel();
              } else {
                switchToNextChannel();
              }
              return;
            }
            if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
              e.preventDefault();
              e.stopPropagation();
              setShowChannelGuide(true);
              return;
            }
          }

          // Controls hidden VOD → show them and focus first button
          e.preventDefault();
          e.stopPropagation();
          resetControlsTimer(); // This sets showControls(true) + updates ref
          setTimeout(() => {
            const firstBtn = document.querySelector('.custom-player-buttons-row .control-btn');
            if (firstBtn) firstBtn.focus();
          }, 50); // Small delay to let React re-render the visible overlay first
          return;
        }

        // Controls visible → let special elements handle left/right natively
        const isSpecial = activeEl?.classList?.contains?.('custom-player-progress-bar-wrapper')
                       || activeEl?.classList?.contains?.('volume-slider')
                       || activeEl?.closest?.('.channel-category-tabs-container');
        if (isSpecial && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
          resetControlsTimer();
          return; // Let the element's own handler process it
        }

        // Navigate between focusable elements
        e.preventDefault();
        e.stopPropagation();
        const player = document.querySelector('.watch-overlay');
        if (!player) return;
        const focusables = Array.from(player.querySelectorAll('.focusable')).filter(el => {
          return el.offsetParent !== null && !el.disabled;
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
        const isInSearch = activeEl?.classList.contains('channel-guide-search-input');
        const isInCategories = activeEl?.closest('.channel-category-tabs-container');
        const isInList = activeEl?.closest('.channel-guide-list');

        if (e.key === 'Escape' || e.key === 'Backspace') {
          e.preventDefault();
          e.stopPropagation();
          setShowChannelGuide(false);
          // Focus the guide list button
          setTimeout(() => {
            const btn = document.querySelector('.custom-player-buttons-row .control-btn');
            if (btn) btn.focus();
          }, 0);
          return;
        }

        if (isInSearch) {
          if (e.key === 'ArrowDown') {
            e.preventDefault(); e.stopPropagation();
            const activeTab = document.querySelector('.channel-category-tab.active') || document.querySelector('.channel-category-tab');
            if (activeTab) activeTab.focus();
            return;
          }
          if (e.key === 'Enter') {
            return; // Let the input behave normally (accept enter/typing)
          }
        }

        if (isInCategories) {
          if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
            e.preventDefault(); e.stopPropagation();
            const tabs = Array.from(document.querySelectorAll('.channel-category-tab'));
            const cur = tabs.indexOf(activeEl);
            const dir = e.key === 'ArrowRight' ? 1 : -1;
            const next = (cur + dir + tabs.length) % tabs.length;
            tabs[next]?.focus();
            setSelectedCategory(tabs[next].dataset.category);
            return;
          }
          if (e.key === 'ArrowDown') {
            e.preventDefault(); e.stopPropagation();
            const firstChannel = document.querySelector('.channel-guide-item');
            if (firstChannel) firstChannel.focus();
            return;
          }
          if (e.key === 'ArrowUp') {
            e.preventDefault(); e.stopPropagation();
            const searchInput = document.querySelector('.channel-guide-search-input');
            if (searchInput) searchInput.focus();
            return;
          }
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault(); e.stopPropagation();
            activeEl.click();
            return;
          }
        }

        if (isInList) {
          if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
            e.preventDefault(); e.stopPropagation();
            const items = Array.from(document.querySelectorAll('.channel-guide-item'));
            const cur = items.indexOf(activeEl);
            if (cur === 0 && e.key === 'ArrowUp') {
              // Move focus to category tabs
              const activeTab = document.querySelector('.channel-category-tab.active') || document.querySelector('.channel-category-tab');
              if (activeTab) activeTab.focus();
              return;
            }
            const dir = e.key === 'ArrowDown' ? 1 : -1;
            const next = (cur + dir + items.length) % items.length;
            items[next]?.focus();
            items[next]?.scrollIntoView({ block: 'nearest' });
            return;
          }
          if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
            e.preventDefault(); e.stopPropagation();
            // Switch categories via arrow keys inside list
            const tabs = Array.from(document.querySelectorAll('.channel-category-tab'));
            const activeTab = document.querySelector('.channel-category-tab.active');
            const curIdx = tabs.indexOf(activeTab);
            const dir = e.key === 'ArrowRight' ? 1 : -1;
            const nextIdx = (curIdx + dir + tabs.length) % tabs.length;
            setSelectedCategory(tabs[nextIdx].dataset.category);
            return;
          }
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault(); e.stopPropagation();
            activeEl.click();
            return;
          }
        }

        // Fallback to avoid double bubble
        if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Enter', ' '].includes(e.key)) {
          return;
        }
      }

      // ── Escape / Backspace ─────────────────────────────────────
      // UX: 1st press → show controls (if hidden). 2nd press within 2s → close player.
      if (e.key === 'Escape' || e.key === 'Backspace') {
        e.preventDefault();
        e.stopPropagation();

        const now = Date.now();

        if (!showControlsRef.current) {
          // Controls hidden → show them on first press
          resetControlsTimer();
          backPressRef.current = now;
        } else if (backPressRef.current > 0 && now - backPressRef.current < 2000) {
          // Controls visible + second press within 2s → close player
          backPressRef.current = 0;
          onClose?.();
          return;
        } else {
          // Controls visible, timer expired or first time → restart timer
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
      if (!showControlsRef.current) {
        resetControlsTimer(); // sets showControls(true) + updates ref
        e.preventDefault(); e.stopPropagation();
        setTimeout(() => {
          const firstBtn = document.querySelector('.custom-player-buttons-row .control-btn');
          if (firstBtn) firstBtn.focus();
        }, 50);
        return;
      }
      resetControlsTimer();
    };

    window.addEventListener('keydown', handleKeyDown, true);
    return () => {
      window.removeEventListener('keydown', handleKeyDown, true);
    };
  // showControls removed from deps — we use showControlsRef to avoid stale closures
  }, [localSource.type, channelList, showChannelGuide, currentChannelIndex, switchToPrevChannel, switchToNextChannel, jumpToChannel]);

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
    const isDash = currentStream.url.toLowerCase().includes('.mpd');
    // Extract referer from headers if available
    const referer = currentStream.referer || (currentStream.headers && (currentStream.headers.referer || currentStream.headers.Referer));
    
    // [antigravity] Use proxy for all remote HTTP streams to bypass CORS, except localhost, DASH (.mpd) and known CORS-supporting hosts like fubohd.com
    const streamUrl = currentStream.url.startsWith('http') && !currentStream.url.includes('localhost') && !isDash && !currentStream.url.includes('fubohd.com')
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
    } else if (isDash) {
      const initDash = () => {
        if (!window.dashjs) {
          console.error("[VideoPlayer] dash.js no está cargado");
          handleStreamError("El reproductor de DASH no se pudo cargar.");
          return;
        }
        try {
          const dashPlayer = window.dashjs.MediaPlayer().create();
          dashRef.current = dashPlayer;
          
          if (referer) {
            dashPlayer.updateSettings({
              streaming: {
                xhr: {
                  headers: {
                    'Referer': referer
                  }
                }
              }
            });
          }

          dashPlayer.initialize(video, streamUrl, !showBrandIntroRef.current);
          
          playerRef.current = new Plyr(video, {
            controls: [], // Hide native controls
            autoplay: !showBrandIntroRef.current
          });
          
          if (!showBrandIntroRef.current) {
            playerRef.current.play().catch((e) => {
              console.log("Autoplay blocked by browser:", e);
            });
          }
        } catch (err) {
          console.error("[VideoPlayer] Error al inicializar dash.js:", err);
          handleStreamError("Error al iniciar la reproducción de DASH.");
        }
      };

      if (window.dashjs) {
        initDash();
      } else {
        console.log("[VideoPlayer] Cargando dash.js desde CDN...");
        const script = document.createElement('script');
        script.src = 'https://cdnjs.cloudflare.com/ajax/libs/dashjs/4.7.4/dash.all.min.js';
        script.async = true;
        script.onload = () => {
          console.log("[VideoPlayer] dash.js cargado correctamente");
          initDash();
        };
        script.onerror = () => {
          console.error("[VideoPlayer] Error al cargar dash.js desde CDN");
          handleStreamError("No se pudo cargar el reproductor de DASH.");
        };
        document.head.appendChild(script);
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
    } else if (!isLoading && !errorText) {
      const playBtn = document.querySelector('.custom-player-buttons-row .control-btn');
      if (playBtn) playBtn.focus();
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
    if (dashRef.current) {
      try {
        dashRef.current.destroy();
      } catch (e) {}
      dashRef.current = null;
    }
  };

  const handleSourceChange = (index) => {
    setActiveStreamIndex(index);
  };

  const switchToOriginalStream = async (idx) => {
    if (!localSource.originalStreams || !localSource.originalStreams[idx]) return;
    const selectedStream = localSource.originalStreams[idx];
    
    setIsLoading(true);
    setErrorText(`Sintonizando ${selectedStream.name || `Servidor ${idx + 1}`}...`);
    setShowServerPopup(false);

    try {
      const res = await fetch(`/api/live/resolve`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          streams: [selectedStream],
          type: localSource.type
        })
      });
      const data = await res.json();
      if (data.success) {
        const updatedSource = {
          ...localSource,
          selectedLanguage: data.selectedLanguage,
          selectedServer: data.selectedServer,
          selectedStreamIndex: idx,
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
        destroyPlayer();
        setRetryCount(0);
        setActiveStreamIndex(idx);
        setLocalSource(updatedSource);
        setTimeout(() => {
          setIsLoading(false);
          setErrorText(null);
          setPlayerKey(prev => prev + 1);
        }, 1000);
      } else {
        setIsLoading(false);
        setErrorText(`No se pudo sintonizar este servidor: ${data.error || 'error desconocido'}`);
      }
    } catch (err) {
      console.error("[VideoPlayer] Error switching stream:", err);
      setIsLoading(false);
      setErrorText("Error de red al sintonizar este servidor.");
    }
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
        <div style={{ textAlign: 'center', maxWidth: '500px', padding: '24px' }} className="glass-panel form-card">
          <h3 style={{ fontSize: '1.3rem', fontWeight: 700, color: 'var(--accent)', marginBottom: '12px' }}>Error de Conexión</h3>
          <p className="text-secondary" style={{ marginBottom: '20px', fontSize: '0.95rem' }}>{errorText}</p>
          
          {localSource.originalStreams && localSource.originalStreams.length > 1 && (
            <div style={{ marginBottom: '20px', textAlign: 'left' }}>
              <p style={{ fontSize: '0.85rem', fontWeight: 600, marginBottom: '8px', color: 'var(--text-secondary)' }}>
                Intentar con otro servidor/enlace disponible:
              </p>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '160px', overflowY: 'auto', paddingRight: '4px' }}>
                {localSource.originalStreams.map((stream, idx) => (
                  <button
                    key={idx}
                    className={`btn-sports-action focusable ${activeStreamIndex === idx ? 'primary' : ''}`}
                    style={{ justifyContent: 'flex-start', width: '100%', padding: '8px 12px', fontSize: '0.85rem' }}
                    tabIndex={0}
                    onClick={() => switchToOriginalStream(idx)}
                  >
                    <span>{stream.name || `Servidor ${idx + 1}`}</span>
                  </button>
                ))}
              </div>
            </div>
          )}

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
              isLive ? (localSource.isSports ? 'Deportes en Vivo' : 'Canal en Vivo') : 'Película'
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
          <iframe
            src={getEmbedUrl()}
            allow="autoplay; fullscreen"
            allowFullScreen
            style={{ width: '100%', height: '100%', border: 'none' }}
          />
        ) : (
          <video
            ref={videoRef}
            className="plyr-video"
            playsInline
            preload="none"
            poster="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"
            style={{ width: '100%', height: '100%', objectFit: 'contain', background: '#000' }}
          />
        )}
      </div>

      {/* ── Bottom controls overlay ──────────────────────────────────── */}
      <div className={`custom-player-overlay ${showControls ? 'visible' : 'hidden'}`}>
        <div className="custom-player-bottom-bar">

          {/* Progress bar (solo películas/series) */}
          {/* Progress bar (solo películas/series) */}
          {!isLive && (
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
            {!isLive && (
              <button className="control-btn focusable" tabIndex={0} onClick={() => {
                if (videoRef.current) videoRef.current.currentTime = Math.max(0, (videoRef.current.currentTime || 0) - 10);
              }}>
                <RotateCcw size={18} />
              </button>
            )}

            {/* Seek forward 10s (no TV) */}
            {!isLive && (
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
            {isLive && channelList?.length > 1 && (
              <>
                <span className="custom-player-time" style={{ color: 'var(--text-muted)', fontWeight: 600 }}>Canales</span>
                <button className="control-btn focusable" tabIndex={0} onClick={switchToPrevChannel} title="Canal Anterior">
                  <SkipBack size={20} />
                </button>
                <button className="control-btn focusable" tabIndex={0} onClick={switchToNextChannel} title="Canal Siguiente">
                  <SkipForward size={20} />
                </button>
                <button className="control-btn focusable" tabIndex={0} onClick={() => setShowChannelGuide(true)} title="Guía de Canales">
                  <List size={20} />
                </button>
              </>
            )}

            <div style={{ flexGrow: 1 }} />

            {/* TV Audio/Subtitle/Server selectors */}
            {isLive && (
              <>
                {(streams.length > 1 || (localSource.originalStreams && localSource.originalStreams.length > 1)) && (
                  <button className="control-btn focusable" tabIndex={0} onClick={() => { setShowServerPopup(!showServerPopup); setShowAudioPopup(false); setShowSubtitlePopup(false); }} title="Servidores">
                    <Layers size={18} />
                  </button>
                )}
                {availableAudioTracks.length > 0 && (
                  <button className="control-btn focusable" tabIndex={0} onClick={() => { setShowAudioPopup(!showAudioPopup); setShowSubtitlePopup(false); setShowServerPopup(false); }} title="Audio">
                    <Globe2 size={18} />
                  </button>
                )}
                {availableSubtitleTracks.length > 0 && (
                  <button className="control-btn focusable" tabIndex={0} onClick={() => { setShowSubtitlePopup(!showSubtitlePopup); setShowAudioPopup(false); setShowServerPopup(false); }} title="Subtítulos">
                    <Subtitles size={18} />
                  </button>
                )}
              </>
            )}

            {/* Volume (VOD or direct Live HLS) */}
            {(localSource.type !== 'tv' || (currentStream && currentStream.resolver === 'direct')) && (
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

            {/* VOD Server alternatives */}
            {!isLive && source?.alternatives && source.alternatives.length > 0 && (
              <button className="control-btn focusable" tabIndex={0} onClick={() => { setShowVODServerPopup(!showVODServerPopup); }} title="Servidores alternativos">
                <Layers size={18} />
              </button>
            )}

            {/* Playback speed (no TV/live) */}
            {!isLive && (
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

        {/* ── Popups for Server, Audio, Subtitles ────────────────────── */}
        {showServerPopup && (streams.length > 1 || (localSource.originalStreams && localSource.originalStreams.length > 1)) && (
          <div className="player-popup-menu focusable-container">
            <div className="player-popup-header">Servidores</div>
            {(localSource.originalStreams || streams).map((stream, idx) => {
              const isActive = activeStreamIndex === idx;
              return (
                <button
                  key={idx}
                  className={`player-popup-item focusable ${isActive ? 'active' : ''}`}
                  tabIndex={0}
                  onClick={() => {
                    if (localSource.originalStreams) {
                      switchToOriginalStream(idx);
                    } else {
                      setActiveStreamIndex(idx);
                      setShowServerPopup(false);
                    }
                  }}
                >
                  <span>{stream.name || `Servidor ${idx + 1}`}</span>
                  {isActive && <span style={{ color: 'var(--primary-light)' }}>✓</span>}
                </button>
              );
            })}
          </div>
        )}

        {showAudioPopup && availableAudioTracks.length > 0 && (
          <div className="player-popup-menu focusable-container">
            <div className="player-popup-header">Idiomas de Audio</div>
            {availableAudioTracks.map((track) => (
              <button
                key={track.id}
                className={`player-popup-item focusable ${activeAudioTrack === track.id ? 'active' : ''}`}
                tabIndex={0}
                onClick={() => {
                  if (hlsRef.current) hlsRef.current.audioTrack = track.id;
                  setActiveAudioTrack(track.id);
                  setShowAudioPopup(false);
                }}
              >
                <span>{track.name || `Pista ${track.id + 1}`}</span>
                {activeAudioTrack === track.id && <span style={{ color: 'var(--primary-light)' }}>✓</span>}
              </button>
            ))}
          </div>
        )}

        {/* ── VOD Server Alternatives Popup ──────────────────────── */}
        {showVODServerPopup && source?.alternatives && source.alternatives.length > 0 && (
          <div className="player-popup-menu focusable-container">
            <div className="player-popup-header">Servidores Alternativos</div>
            {source.alternatives.map((alt, idx) => {
              const isActive = alt.server === source.selectedServer;
              return (
                <button
                  key={idx}
                  className={`player-popup-item focusable ${isActive ? 'active' : ''}`}
                  tabIndex={0}
                  onClick={() => {
                    if (!isActive) {
                      onSourceChange?.({
                        ...source,
                        selectedServer: alt.server,
                        streams: [{ name: alt.server, url: alt.url, type: 'video/mp4', headers: {}, quality: alt.quality || 'HD', resolver: 'direct' }]
                      });
                    }
                    setShowVODServerPopup(false);
                  }}
                >
                  <span>{alt.server} {alt.language ? `(${alt.language})` : ''}</span>
                  <small style={{ color: 'var(--text-muted)', marginLeft: 8 }}>{alt.score ? `Score: ${alt.score}` : ''}</small>
                  {isActive && <span style={{ color: 'var(--primary-light)', marginLeft: 8 }}>✓</span>}
                </button>
              );
            })}
          </div>
        )}

        {showSubtitlePopup && (
          <div className="player-popup-menu focusable-container">
            <div className="player-popup-header">Subtítulos</div>
            <button
              className={`player-popup-item focusable ${activeSubtitleTrack === -1 ? 'active' : ''}`}
              tabIndex={0}
              onClick={() => {
                if (hlsRef.current) hlsRef.current.subtitleTrack = -1;
                setActiveSubtitleTrack(-1);
                setShowSubtitlePopup(false);
              }}
            >
              <span>Desactivados</span>
              {activeSubtitleTrack === -1 && <span style={{ color: 'var(--primary-light)' }}>✓</span>}
            </button>
            {availableSubtitleTracks.map((track) => (
              <button
                key={track.id}
                className={`player-popup-item focusable ${activeSubtitleTrack === track.id ? 'active' : ''}`}
                tabIndex={0}
                onClick={() => {
                  if (hlsRef.current) hlsRef.current.subtitleTrack = track.id;
                  setActiveSubtitleTrack(track.id);
                  setShowSubtitlePopup(false);
                }}
              >
                <span>{track.name || track.lang || `Sub ${track.id + 1}`}</span>
                {activeSubtitleTrack === track.id && <span style={{ color: 'var(--primary-light)' }}>✓</span>}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* ── Premium Zapping Channel Info Banner ──────────────────────────── */}
      {isLive && showZappingBanner && (
        <div className="channel-zapping-banner">
          <div className="zap-logo-container">
            {localSource.poster || localSource.thumbnailUrl || localSource.logo ? (
              <img
                src={localSource.poster || localSource.thumbnailUrl || localSource.logo}
                alt={localSource.title}
                className="zap-logo"
                onError={(e) => { e.target.style.display = 'none'; }}
              />
            ) : (
              <Tv size={24} style={{ color: 'var(--primary-light)' }} />
            )}
          </div>
          <div className="zap-info-col">
            <div className="zap-num-title-row">
              {currentChannelIndex >= 0 && (
                <span className="zap-channel-num">
                  {String(currentChannelIndex + 1).padStart(3, '0')}
                </span>
              )}
              <h3 className="zap-channel-title">{localSource.title}</h3>
            </div>
            <div className="zap-meta-row">
              <span className="zap-channel-cat">
                {getNormalizedTVCategory(localSource)}
              </span>
              <span className="zap-stream-info">
                {currentStream?.type === 'hls' ? 'HLS Direct' : (currentStream?.resolver || 'IPTV Direct')}
              </span>
              {currentStream?.name && (
                <span className="zap-stream-info">
                  Servidor: {currentStream.name}
                </span>
              )}
            </div>
          </div>
          <div className="zap-live-indicator">
            <span className="zap-live-dot" />
            <span>En vivo</span>
          </div>
        </div>
      )}

      {/* ── Channel Guide Drawer (Mini-EPG) ────────────────────────── */}
      {isLive && channelList && (
        <div ref={guideRef} className={`player-channel-drawer ${showChannelGuide ? 'visible' : 'hidden'}`}>
          <div className="player-drawer-header">
            <h3 className="player-drawer-title">Guía de Canales</h3>
            <button className="player-drawer-close focusable" tabIndex={0} onClick={() => setShowChannelGuide(false)}>
              <X size={22} />
            </button>
          </div>

          <div className="player-drawer-content" style={{ gap: '12px' }}>
            {/* Search Input */}
            <div className="channel-guide-search-wrapper">
              <input
                type="text"
                className="channel-guide-search-input focusable"
                placeholder="Buscar canal..."
                value={guideSearchQuery}
                onChange={(e) => setGuideSearchQuery(e.target.value)}
                tabIndex={0}
              />
              <Search className="channel-guide-search-icon" size={16} />
              {guideSearchQuery && (
                <button
                  className="channel-guide-search-clear"
                  onClick={() => setGuideSearchQuery('')}
                  title="Limpiar"
                >
                  <X size={14} />
                </button>
              )}
            </div>

            {/* Category tabs */}
            {categories.length > 1 && (
              <div className="channel-category-tabs-container">
                {categories.map((cat) => (
                  <button
                    key={cat}
                    data-category={cat}
                    className={`channel-category-tab focusable ${selectedCategory === cat ? 'active' : ''}`}
                    tabIndex={0}
                    onClick={() => setSelectedCategory(cat)}
                  >
                    {cat}
                  </button>
                ))}
              </div>
            )}

            {/* Channel List */}
            <div className="channel-guide-list">
              {filteredChannels.map((ch, idx) => {
                const isActive = localSource.id === ch.id;
                const channelNum = String(idx + 1).padStart(3, '0');
                const chLogo = ch.poster || ch.thumbnailUrl || ch.logo;
                const chCat = getNormalizedTVCategory(ch);
                return (
                  <button
                    key={ch.id || idx}
                    className={`channel-guide-item focusable ${isActive ? 'active' : ''}`}
                    tabIndex={0}
                    onClick={() => jumpToChannel(ch)}
                  >
                    <span className="channel-guide-item-num">{channelNum}</span>
                    <div className="channel-guide-item-logo">
                      {chLogo ? (
                        <img src={chLogo} alt={ch.title} onError={(e) => { e.target.style.display = 'none'; }} />
                      ) : (
                        <Tv size={14} />
                      )}
                    </div>
                    <div className="channel-guide-item-title-col">
                      <span className="channel-guide-item-name">{ch.title}</span>
                      <span className="channel-guide-item-tag">{chCat}</span>
                    </div>
                    {isActive && <div className="channel-guide-item-active-dot" />}
                  </button>
                );
              })}
              {filteredChannels.length === 0 && (
                <p style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.85rem', marginTop: '20px' }}>
                  No se encontraron canales.
                </p>
              )}
            </div>
          </div>
        </div>
      )}

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
        <LoadingScreen
          type="player"
          title={localSource.title}
          onClose={onClose}
          closeButtonRef={closeButtonRef}
        />
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
            poster="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"
            onEnded={handleBrandIntroEnded}
            onError={handleBrandIntroError}
            onTimeUpdate={handleBrandTimeUpdate}
            className="brand-intro-video"
            style={{ background: '#000' }}
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
