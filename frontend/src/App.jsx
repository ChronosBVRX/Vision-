import React, { useState, useMemo, useEffect, Suspense, lazy } from 'react';
import {
  Home as HomeIcon, Settings, Film, Tv, Play, Menu, X,
  Globe, PlayCircle, ChevronDown, ChevronRight, Star, Layers
} from 'lucide-react';
import Home from './pages/Home';
import Movies from './pages/Movies';
import Series from './pages/Series';
const Admin = lazy(() => import('./pages/Admin'));
const Sports = lazy(() => import('./pages/Sports'));
import useSpatialNavigation from './hooks/useSpatialNavigation';
import { useCatalog } from './context/CatalogContext.jsx';
import logoImg from './assets/logo.png';
import Bootloader from './components/Bootloader.jsx';

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

export default function App() {
  const [currentPage, setCurrentPage]               = useState('home');
  const [selectedCategoryFilter, setSelectedCategoryFilter] = useState('all');
  const [sidebarOpen, setSidebarOpen]               = useState(false);
  const [tvSubmenuOpen, setTvSubmenuOpen]           = useState(false);
  const [animeSubmenuOpen, setAnimeSubmenuOpen]     = useState(false);
  const [animeTab, setAnimeTab]                     = useState('Series');
  
  const { sources, categories, categoriesDetailed } = useCatalog();
  
  useSpatialNavigation(true);

  // Auto-focus first submenu item when submenus open
  useEffect(() => {
    if (tvSubmenuOpen) setTimeout(() => {
      const first = document.querySelector('.nav-subitem.focusable');
      if (first) first.focus();
    }, 50);
  }, [tvSubmenuOpen]);
  useEffect(() => {
    if (animeSubmenuOpen) setTimeout(() => {
      const first = document.querySelector('.nav-subitem.focusable');
      if (first) first.focus();
    }, 50);
  }, [animeSubmenuOpen]);

  // Listen for sidebar-open event from useSpatialNavigation
  useEffect(() => {
    const handleSidebarOpen = () => setSidebarOpen(true);
    window.addEventListener('sidebar-open', handleSidebarOpen);
    return () => window.removeEventListener('sidebar-open', handleSidebarOpen);
  }, []);

  const selectPage = (page) => {
    setCurrentPage(page);
    setSidebarOpen(false);
    setTvSubmenuOpen(false);
    setAnimeSubmenuOpen(false);
    if (page !== 'home') setSelectedCategoryFilter('all');
    if (document.activeElement) document.activeElement.blur();
    setTimeout(() => {
      const firstMainItem = document.querySelector('.main-content .focusable');
      if (firstMainItem) firstMainItem.focus();
    }, 100);
  };

  const selectCategory = (cat) => {
    setSelectedCategoryFilter(cat);
    setCurrentPage('home');
    setSidebarOpen(false);
    if (cat !== 'Canales en Vivo') {
      setTvSubmenuOpen(false);
    }
    setAnimeSubmenuOpen(false);
    if (document.activeElement) document.activeElement.blur();
    setTimeout(() => {
      const firstMainItem = document.querySelector('.main-content .focusable');
      if (firstMainItem) firstMainItem.focus();
    }, 100);
  };

  // TV channel subcategories (unified and cleaned up)
  const tvCategories = useMemo(() => {
    const set = new Set();
    sources.forEach(s => {
      if (s.type === 'tv') {
        set.add(getNormalizedTVCategory(s));
      }
    });
    return Array.from(set).sort();
  }, [sources]);

  return (
    <Bootloader>
      <div className="app-container">

        {/* ── Sidebar ──────────────────────────────────────────────────────── */}
      <aside 
        className={`sidebar ${sidebarOpen ? 'open' : ''}`} 
        tabIndex={-1}
        onFocus={() => setSidebarOpen(true)}
        onBlur={(e) => {
          if (!e.currentTarget.contains(e.relatedTarget)) {
            setSidebarOpen(false);
          }
        }}
      >
        {/* Logo */}
        <div className="logo">
          <img src={logoImg} alt="Vision+" />
        </div>

        <ul className="nav-list" style={{ flexGrow: 1 }}>

          {/* Inicio */}
          <li
            className={`nav-item focusable ${currentPage === 'home' && selectedCategoryFilter === 'all' ? 'active' : ''}`}
            tabIndex={0}
            onClick={() => { selectPage('home'); setSelectedCategoryFilter('all'); }}
            onKeyDown={e => { if (e.key === 'Enter') { selectPage('home'); setSelectedCategoryFilter('all'); } }}
          >
            <HomeIcon size={24} />
            <span>Inicio</span>
          </li>

          {/* Deportes */}
          <li
            className={`nav-item focusable ${currentPage === 'sports' ? 'active' : ''}`}
            tabIndex={0}
            onClick={() => selectPage('sports')}
            onKeyDown={e => { if (e.key === 'Enter') selectPage('sports'); }}
          >
            <PlayCircle size={24} />
            <span>Deportes en Vivo</span>
          </li>


          {/* ── Section divider ──────────────────────────────────────────── */}
          <li className="nav-divider" />
          <li className="nav-section-label">Contenido</li>

          {/* Canales en Vivo (with subcategories) */}
          <li
            className={`nav-item focusable ${currentPage === 'home' && (selectedCategoryFilter === 'Canales en Vivo' || tvCategories.includes(selectedCategoryFilter)) ? 'active' : ''}`}
            tabIndex={0}
            style={{ display: 'flex', alignItems: 'center' }}
            onClick={() => {
              setTvSubmenuOpen(o => !o);
              if (currentPage !== 'home' || !tvCategories.includes(selectedCategoryFilter)) {
                selectCategory('Canales en Vivo');
              }
            }}
            onKeyDown={e => {
              if (e.key === 'Enter' || e.key === 'ArrowRight') { e.preventDefault(); e.stopPropagation(); setTvSubmenuOpen(true); selectCategory('Canales en Vivo'); }
              if (e.key === 'ArrowLeft' && tvSubmenuOpen) { e.preventDefault(); e.stopPropagation(); setTvSubmenuOpen(false); }
            }}
          >
            <Tv size={24} />
            <span style={{ flexGrow: 1 }}>Canales en Vivo</span>
            {tvCategories.length > 1 && (tvSubmenuOpen ? <ChevronDown size={14} /> : <ChevronRight size={14} />)}
          </li>

          {tvSubmenuOpen && (
            <>
              <li
                className={`nav-subitem focusable ${currentPage === 'home' && selectedCategoryFilter === 'Canales en Vivo' ? 'active' : ''}`}
                tabIndex={0}
                onClick={() => selectCategory('Canales en Vivo')}
                onKeyDown={e => { if (e.key === 'Enter') selectCategory('Canales en Vivo'); }}
              >
                🌐 Todos
              </li>
              {tvCategories.filter(cat => cat !== 'Variedades / General').map((cat, i) => (
                <li
                  key={i}
                  className={`nav-subitem focusable ${currentPage === 'home' && selectedCategoryFilter === cat ? 'active' : ''}`}
                  tabIndex={0}
                  onClick={() => selectCategory(cat)}
                  onKeyDown={e => { if (e.key === 'Enter') selectCategory(cat); }}
                >
                  {cat}
                </li>
              ))}
              {tvCategories.includes('Variedades / General') && (
                <li
                  className={`nav-subitem focusable ${currentPage === 'home' && selectedCategoryFilter === 'Variedades / General' ? 'active' : ''}`}
                  tabIndex={0}
                  onClick={() => selectCategory('Variedades / General')}
                  onKeyDown={e => { if (e.key === 'Enter') selectCategory('Variedades / General'); }}
                >
                  Variedades / General
                </li>
              )}
            </>
          )}

          {/* Películas → goes to new Movies page */}
          <li
            className={`nav-item focusable ${currentPage === 'movies' ? 'active' : ''}`}
            tabIndex={0}
            onClick={() => selectPage('movies')}
            onKeyDown={e => { if (e.key === 'Enter') selectPage('movies'); }}
          >
            <Film size={24} />
            <span>Películas</span>
          </li>

          {/* Series → goes to new Series page */}
          <li
            className={`nav-item focusable ${currentPage === 'series' ? 'active' : ''}`}
            tabIndex={0}
            onClick={() => selectPage('series')}
            onKeyDown={e => { if (e.key === 'Enter') selectPage('series'); }}
          >
            <Layers size={24} />
            <span>Series</span>
          </li>



          {/* Anime → expandable submenu */}
          <li
            className={`nav-item focusable ${currentPage === 'anime' ? 'active' : ''}`}
            tabIndex={0}
            style={{ display: 'flex', alignItems: 'center' }}
            onClick={() => setAnimeSubmenuOpen(o => !o)}
            onKeyDown={e => {
              if (e.key === 'Enter' || e.key === 'ArrowRight') { e.preventDefault(); e.stopPropagation(); setAnimeSubmenuOpen(true); }
              if (e.key === 'ArrowLeft' && animeSubmenuOpen) { e.preventDefault(); e.stopPropagation(); setAnimeSubmenuOpen(false); }
            }}
          >
            <div style={{
              width: 24, height: 24, borderRadius: '50%', background: '#ff3366', 
              color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', 
              fontWeight: 'bold', fontSize: '12px'
            }}>A</div>
            <span style={{ flexGrow: 1, color: currentPage === 'anime' ? '#ff3366' : 'inherit' }}>Anime</span>
            {animeSubmenuOpen ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          </li>

          {animeSubmenuOpen && (
            <>
              <li
                className={`nav-subitem focusable ${currentPage === 'anime' && animeTab === 'Películas' ? 'active' : ''}`}
                tabIndex={0}
                onClick={() => { setAnimeTab('Películas'); selectPage('anime'); }}
              >
                <Film size={16} />
                Películas
              </li>
              <li
                className={`nav-subitem focusable ${currentPage === 'anime' && animeTab === 'Series' ? 'active' : ''}`}
                tabIndex={0}
                onClick={() => { setAnimeTab('Series'); selectPage('anime'); }}
              >
                <Tv size={16} />
                Series
              </li>
            </>
          )}

        </ul>

        {/* Admin link */}
        <div className="sidebar-footer">
          <button
            className={`nav-item focusable ${currentPage === 'admin' ? 'active' : ''}`}
            tabIndex={0}
            onClick={() => selectPage('admin')}
            style={{ width: '100%', border: 'none', background: 'transparent', textAlign: 'left', cursor: 'pointer' }}
          >
            <Settings size={24} />
            <span>Gestionar Fuentes</span>
          </button>
        </div>
      </aside>

      {/* ── Main Content ──────────────────────────────────────────────────── */}
      <main className="main-content">
        <Suspense fallback={<div className="page-loading"><div className="spin-anim" style={{width:36,height:36,borderWidth:3}} /></div>}>
          {currentPage === 'home' && (
            <Home
              selectedCategoryFilter={selectedCategoryFilter}
              setSelectedCategoryFilter={setSelectedCategoryFilter}
            />
          )}
          {currentPage === 'admin' && <Admin />}
          {currentPage === 'sports' && <Sports />}
          {currentPage === 'movies' && <Movies contentType="movie" />}
          {currentPage === 'series' && <Series />}
          {currentPage === 'anime' && <Movies contentType={animeTab === 'Películas' ? 'anime_movie' : 'anime_series'} />}
        </Suspense>
      </main>
    </div>
    </Bootloader>
  );
}
