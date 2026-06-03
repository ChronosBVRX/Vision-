import React, { useState, useEffect, useMemo } from 'react';
import {
  Home as HomeIcon, Settings, Film, Tv, Play, Menu, X,
  Globe, PlayCircle, ChevronDown, ChevronRight, Star, Layers
} from 'lucide-react';
import Home from './pages/Home';
import Admin from './pages/Admin';
import Sports from './pages/Sports';
import Movies from './pages/Movies';
import Series from './pages/Series';
import PlutoTV from './pages/PlutoTV';
import useSpatialNavigation from './hooks/useSpatialNavigation';
import logoImg from './assets/logo.png';

function getNormalizedTVCategoryName(catName) {
  if (!catName) return 'Variedades / General';
  let clean = catName.replace(/^(Planeta Play - |Pluto TV - |Canales - )/i, '').trim();
  if (clean.toLowerCase() === 'canales en vivo' || clean.toLowerCase() === 'general' || clean.toLowerCase() === 'importado' || !clean) {
    return 'Variedades / General';
  }
  return clean;
}

export default function App() {
  const [currentPage, setCurrentPage]               = useState('home');
  const [categories, setCategories]                 = useState([]);
  const [categoriesDetailed, setCategoriesDetailed] = useState([]);
  const [selectedCategoryFilter, setSelectedCategoryFilter] = useState('all');
  const [sidebarOpen, setSidebarOpen]               = useState(false);
  const [tvSubmenuOpen, setTvSubmenuOpen]           = useState(false);
  const [plutoSubmenuOpen, setPlutoSubmenuOpen]     = useState(false);
  const [plutoTab, setPlutoTab]                     = useState('Todas');
  const [animeSubmenuOpen, setAnimeSubmenuOpen]     = useState(false);
  const [animeTab, setAnimeTab]                     = useState('Series');
  
  useSpatialNavigation(true);

  useEffect(() => { fetchCategories(); }, []);

  const fetchCategories = () => {
    fetch('/api/sources')
      .then(r => r.json())
      .then(data => {
        setCategories(data.categories || []);
        setCategoriesDetailed(data.categoriesDetailed || []);
      })
      .catch(err => console.error('Error fetching categories:', err));
  };

  const selectPage = (page) => {
    setCurrentPage(page);
    setSidebarOpen(false);
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
    if (document.activeElement) document.activeElement.blur();
    setTimeout(() => {
      const firstMainItem = document.querySelector('.main-content .focusable');
      if (firstMainItem) firstMainItem.focus();
    }, 100);
  };

  // TV channel subcategories (unified and cleaned up)
  const tvCategories = useMemo(() => {
    const set = new Set();
    categoriesDetailed.forEach(c => {
      if (c.type === 'tv') {
        const norm = getNormalizedTVCategoryName(c.name);
        set.add(norm);
      }
    });
    return Array.from(set).sort();
  }, [categoriesDetailed]);

  return (
    <div className="app-container">

      {/* ── Sidebar ──────────────────────────────────────────────────────── */}
      <aside className={`sidebar`} tabIndex={-1}>
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
            onKeyDown={e => { if (e.key === 'Enter') { setTvSubmenuOpen(o => !o); selectCategory('Canales en Vivo'); } }}
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

          {/* Pluto TV → expandable submenu */}
          <li
            className={`nav-item focusable ${currentPage === 'plutotv' ? 'active' : ''}`}
            tabIndex={0}
            style={{ display: 'flex', alignItems: 'center' }}
            onClick={() => setPlutoSubmenuOpen(o => !o)}
          >
            <div style={{
              width: 24, height: 24, borderRadius: '50%', background: '#ffcc00', 
              color: 'black', display: 'flex', alignItems: 'center', justifyContent: 'center', 
              fontWeight: 'bold', fontSize: '12px'
            }}>P</div>
            <span style={{ flexGrow: 1, color: currentPage === 'plutotv' ? '#ffcc00' : 'inherit' }}>Pluto TV</span>
            {plutoSubmenuOpen ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          </li>

          {plutoSubmenuOpen && (
            <>
              <li
                className={`nav-subitem focusable ${currentPage === 'plutotv' && plutoTab === 'Canales en Vivo' ? 'active' : ''}`}
                tabIndex={0}
                onClick={() => { setPlutoTab('Canales en Vivo'); selectPage('plutotv'); }}
              >
                <Tv size={16} />
                TV en Vivo
              </li>
              <li
                className={`nav-subitem focusable ${currentPage === 'plutotv' && plutoTab === 'Películas' ? 'active' : ''}`}
                tabIndex={0}
                onClick={() => { setPlutoTab('Películas'); selectPage('plutotv'); }}
              >
                <Film size={16} />
                Películas o Series Bajo Demanda
              </li>
            </>
          )}

          {/* Anime → expandable submenu */}
          <li
            className={`nav-item focusable ${currentPage === 'anime' ? 'active' : ''}`}
            tabIndex={0}
            style={{ display: 'flex', alignItems: 'center' }}
            onClick={() => setAnimeSubmenuOpen(o => !o)}
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
        {currentPage === 'plutotv' && <PlutoTV initialTab={plutoTab} />}
        {currentPage === 'anime' && <Movies contentType={animeTab === 'Películas' ? 'anime_movie' : 'anime_series'} />}
      </main>
    </div>
  );
}
