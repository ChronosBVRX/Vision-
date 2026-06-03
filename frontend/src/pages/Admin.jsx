import React, { useState, useEffect } from 'react';
import { Plus, Trash2, Edit2, Download, Upload, List, Film, Tv, PlusCircle, CheckCircle, AlertCircle, RefreshCw, X } from 'lucide-react';

export default function Admin() {
  const [sources, setSources] = useState([]);
  const [categories, setCategories] = useState([]);
  const [activeTab, setActiveTab] = useState('list'); // 'list' | 'add' | 'categories' | 'm3u'
  
  // Alert banner states
  const [alert, setAlert] = useState(null); // { type: 'success'|'error'|'info', message: '' }
  // Form states for adding/editing a source
  const [expandedCategory, setExpandedCategory] = useState(null);
  const [editingId, setEditingId] = useState(null);
  const [title, setTitle] = useState('');
  const [type, setType] = useState('movie'); // 'movie' | 'tv' | 'series' | 'anime_movie' | 'anime_series'
  const [category, setCategory] = useState('');
  const [poster, setPoster] = useState('');
  const [description, setDescription] = useState('');
  const [tmdbId, setTmdbId] = useState('');
  const [streams, setStreams] = useState([{ name: 'Servidor 1', url: '', resolver: 'direct' }]);

  // Category management state
  const [newCategoryName, setNewCategoryName] = useState('');
  const [categoriesDetailed, setCategoriesDetailed] = useState([]);
  const [newCategoryType, setNewCategoryType] = useState('movie');
  const [m3uType, setM3uType] = useState('tv');

  // M3U import state
  const [m3uUrl, setM3uUrl] = useState('');
  const [m3uRaw, setM3uRaw] = useState('');
  const [m3uCategory, setM3uCategory] = useState('IPTV Importado');

  // Basic Auth State
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [authInput, setAuthInput] = useState('');

  useEffect(() => {
    const savedToken = localStorage.getItem('admin_token');
    if (savedToken === 'visionplus2026') {
      setIsAuthenticated(true);
      fetchSources();
    }
  }, []);

  useEffect(() => {
    const matchingCats = categoriesDetailed.filter(c => c.type === type);
    if (matchingCats.length > 0) {
      if (!matchingCats.some(c => c.name === category)) {
        setCategory(matchingCats[0].name);
      }
    } else {
      setCategory('');
    }
  }, [type, categoriesDetailed]);

  const handleLogin = (e) => {
    e.preventDefault();
    if (authInput === 'admin123' || authInput === 'visionplus2026') { // Hardcoded for simplicity as requested by basic auth
      localStorage.setItem('admin_token', 'visionplus2026');
      setIsAuthenticated(true);
      fetchSources();
    } else {
      showAlert('error', 'Contraseña incorrecta');
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('admin_token');
    setIsAuthenticated(false);
  };

  const showAlert = (type, message) => {
    setAlert({ type, message });
    setTimeout(() => setAlert(null), 5000);
  };

  const fetchSources = () => {
    fetch('/api/sources?includeCatalog=true')
      .then(res => res.json())
      .then(data => {
        setSources(data.sources || []);
        setCategories(data.categories || []);
        setCategoriesDetailed(data.categoriesDetailed || []);
        if (data.categories && data.categories.length > 0 && !category) {
          setCategory(data.categories[0]);
        }
      })
      .catch(err => {
        console.error("Error loading sources:", err);
        showAlert('error', 'Error al conectar con el servidor.');
      });
  };

  // Add a stream row to the form
  const addStreamField = () => {
    setStreams([...streams, { name: `Servidor ${streams.length + 1}`, url: '', resolver: 'direct' }]);
  };

  // Remove a stream row
  const removeStreamField = (index) => {
    const newStreams = streams.filter((_, i) => i !== index);
    setStreams(newStreams);
  };

  // Update a stream field value
  const handleStreamChange = (index, field, value) => {
    const newStreams = [...streams];
    newStreams[index][field] = value;
    setStreams(newStreams);
  };

  // Handle source form submit (Create or Edit)
  const handleSubmitSource = (e) => {
    e.preventDefault();
    
    if (!title || !category) {
      showAlert('error', 'El título y la categoría son campos obligatorios.');
      return;
    }

    const cleanedStreams = streams.filter(s => s.url.trim() !== '' || s.resolver === 'vidsrc');
    if (cleanedStreams.length === 0) {
      showAlert('error', 'Debes añadir al menos un enlace o configurar el servidor (ej: VidSrc).');
      return;
    }

    const sourceData = {
      title,
      type,
      category,
      poster: poster || 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=400',
      description,
      tmdbId: tmdbId || null,
      streams: cleanedStreams
    };

    const method = editingId ? 'PUT' : 'POST';
    const endpoint = editingId ? `/api/sources/${editingId}` : '/api/sources';

    fetch(endpoint, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(sourceData)
    })
      .then(res => {
        if (!res.ok) throw new Error('Error al guardar la fuente.');
        return res.json();
      })
      .then(() => {
        showAlert('success', editingId ? 'Fuente actualizada correctamente.' : 'Nueva fuente creada.');
        resetSourceForm();
        fetchSources();
        setActiveTab('list');
      })
      .catch(err => {
        showAlert('error', err.message);
      });
  };

  // Edit action
  const handleEditSource = (source) => {
    setEditingId(source.id);
    setTitle(source.title);
    setType(source.type);
    setCategory(source.category);
    setPoster(source.poster);
    setDescription(source.description || '');
    setTmdbId(source.tmdbId || '');
    setStreams(source.streams && source.streams.length > 0 ? source.streams : [{ name: 'Servidor 1', url: '', resolver: 'direct' }]);
    setActiveTab('add');
  };

  // Delete action
  const handleDeleteSource = (id) => {
    if (!window.confirm('¿Estás seguro de que deseas eliminar esta fuente?')) return;

    fetch(`/api/sources/${id}`, { method: 'DELETE' })
      .then(res => {
        if (!res.ok) throw new Error('Error al eliminar la fuente.');
        return res.json();
      })
      .then(() => {
        showAlert('success', 'Fuente eliminada correctamente.');
        fetchSources();
      })
      .catch(err => {
        showAlert('error', err.message);
      });
  };

  const resetSourceForm = () => {
    setEditingId(null);
    setTitle('');
    setType('movie');
    const firstMovieCat = categoriesDetailed.find(c => c.type === 'movie');
    setCategory(firstMovieCat ? firstMovieCat.name : (categories[0] || ''));
    setPoster('');
    setDescription('');
    setTmdbId('');
    setStreams([{ name: 'Servidor 1', url: '', resolver: 'direct' }]);
  };

  // Categories API handlers
  const handleAddCategory = (e) => {
    e.preventDefault();
    if (!newCategoryName.trim()) return;

    fetch('/api/categories', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: newCategoryName.trim(), type: newCategoryType })
    })
      .then(res => {
        if (!res.ok) throw new Error('La categoría ya existe o es inválida.');
        return res.json();
      })
      .then(data => {
        showAlert('success', 'Categoría añadida con éxito.');
        setCategories(data);
        setNewCategoryName('');
        fetchSources();
      })
      .catch(err => showAlert('error', err.message));
  };

  const handleDeleteCategory = (name) => {
    if (!window.confirm(`¿Estás seguro de que deseas eliminar la categoría "${name}"?`)) return;

    fetch('/api/categories', {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name })
    })
      .then(res => {
        if (!res.ok) throw new Error('Error al eliminar la categoría.');
        return res.json();
      })
      .then(data => {
        showAlert('success', 'Fuente eliminada y sus elementos han sido removidos.');
        setCategories(data);
        fetchSources();
      })
      .catch(err => showAlert('error', err.message));
  };

  // M3U Playlist Import handler
  const handleImportM3U = (e) => {
    e.preventDefault();
    if (!m3uUrl && !m3uRaw) {
      showAlert('error', 'Debes ingresar una URL o pegar el contenido M3U.');
      return;
    }

    showAlert('info', 'Importando lista, por favor espera...');

    fetch('/api/import-m3u', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        url: m3uUrl || null,
        rawText: m3uRaw || null,
        category: m3uCategory,
        type: m3uType
      })
    })
      .then(res => {
        if (!res.ok) throw new Error('Error al procesar la lista M3U.');
        return res.json();
      })
      .then(data => {
        showAlert('success', data.message);
        setM3uUrl('');
        setM3uRaw('');
        fetchSources();
        setActiveTab('list');
      })
      .catch(err => {
        showAlert('error', err.message);
      });
  };

  // Auto-detect metadata using TMDB ID
  const handleAutoFillTMDB = () => {
    if (!tmdbId) {
      showAlert('error', 'Ingresa un ID de TMDB primero (ej: 550 para Fight Club).');
      return;
    }

    showAlert('info', 'Buscando datos de la película...');
    
    // We fetch TMDB directly from client side. Note: TMDB requires API key. 
    // If not configured, we'll try a fallback or give placeholder feedback.
    // For this app, let's pull details from a free, public movie API proxy or construct details
    // We can fetch from public TMDB API endpoint using an public access token or key if available,
    // or search. To keep it robust without requiring the user to have a TMDB key immediately,
    // we use a public proxy or fallback.
    const isShow = type === 'series';
    const apiType = isShow ? 'tv' : 'movie';
    
    fetch(`https://api.themoviedb.org/3/${apiType}/${tmdbId}?api_key=8414ab200e50f3ab285b5145b2fef42c&language=es-ES`)
      .then(res => {
        if (!res.ok) throw new Error('No se encontró contenido con ese ID en TMDB.');
        return res.json();
      })
      .then(data => {
        setTitle(data.title || data.name || '');
        setDescription(data.overview || '');
        if (data.poster_path) {
          setPoster(`https://image.tmdb.org/t/p/w500${data.poster_path}`);
        }
        
        // Auto add a VidSrc streaming link since we have the TMDB ID!
        const existingVidSrc = streams.find(s => s.resolver === 'vidsrc');
        if (!existingVidSrc) {
          setStreams([
            { name: 'VidSrc (Auto)', url: '', resolver: 'vidsrc' },
            ...streams.filter(s => s.url !== '')
          ]);
        }
        
        showAlert('success', 'Metadatos cargados con éxito desde TMDB.');
      })
      .catch(err => {
        showAlert('error', err.message + ' Recuerda ingresar el ID correcto.');
      });
  };

  if (!isAuthenticated) {
    return (
      <div className="admin-container" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <div className="glass-panel" style={{ width: '100%', maxWidth: '400px', padding: '32px' }}>
          <h2 style={{ textAlign: 'center', marginBottom: '24px', color: '#fff' }}>Acceso Administrativo</h2>
          {alert && (
            <div className={`alert-banner ${alert.type}`} style={{ marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '10px' }}>
              {alert.type === 'error' && <AlertCircle size={18} />}
              <span style={{ flex: 1 }}>{alert.message}</span>
              <button className="focusable" tabIndex={0} onClick={() => setAlert(null)} style={{ background: 'none', border: 'none', color: 'white', cursor: 'pointer', padding: '4px' }}>
                <X size={16} />
              </button>
            </div>
          )}
          <form onSubmit={handleLogin}>
            <div className="form-group" style={{ marginBottom: '24px' }}>
              <label className="form-label">Contraseña</label>
              <input 
                type="password" 
                className="form-input" 
                value={authInput}
                onChange={(e) => setAuthInput(e.target.value)}
                placeholder="Ingresa la contraseña..."
                autoFocus
              />
            </div>
            <button type="submit" className="btn btn-primary focusable" tabIndex={0} style={{ width: '100%' }}>
              Entrar
            </button>
          </form>
        </div>
      </div>
    );
  }

  return (
    <div>
      {/* Alert Banners */}
      {alert && (
        <div className={`alert-banner ${alert.type}`} style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          {alert.type === 'success' && <CheckCircle size={18} />}
          {alert.type === 'error' && <AlertCircle size={18} />}
          {alert.type === 'info' && <RefreshCw className="animate-spin" size={18} />}
          <span style={{ flex: 1 }}>{alert.message}</span>
          <button className="focusable" tabIndex={0} onClick={() => setAlert(null)} style={{ background: 'none', border: 'none', color: 'white', cursor: 'pointer', padding: '4px' }}>
            <X size={16} />
          </button>
        </div>
      )}

      <div className="tab-container" style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
        <button 
          className={`tab-btn focusable ${activeTab === 'list' ? 'active' : ''}`}
          tabIndex={0}
          onClick={() => { setActiveTab('list'); resetSourceForm(); }}
        >
          <List size={16} style={{ marginRight: '6px', verticalAlign: 'middle' }} />
          Gestión de Fuentes
        </button>
        <button 
          className={`tab-btn focusable ${activeTab === 'add' ? 'active' : ''}`}
          tabIndex={0}
          onClick={() => { setActiveTab('add'); if (!editingId) resetSourceForm(); }}
        >
          <PlusCircle size={16} style={{ marginRight: '6px', verticalAlign: 'middle' }} />
          {editingId ? 'Editar Elemento' : 'Añadir Elemento Manual'}
        </button>
        <button 
          className={`tab-btn focusable ${activeTab === 'm3u' ? 'active' : ''}`}
          tabIndex={0}
          onClick={() => setActiveTab('m3u')}
        >
          <Download size={16} style={{ marginRight: '6px', verticalAlign: 'middle' }} />
          Importar Fuente M3U
        </button>
        <button 
          className={`tab-btn focusable ${activeTab === 'discovery' ? 'active' : ''}`}
          tabIndex={0}
          onClick={() => setActiveTab('discovery')}
        >
          <RefreshCw size={16} style={{ marginRight: '6px', verticalAlign: 'middle' }} />
          Descubrir Fuentes (Auto)
        </button>
        <button 
          className="tab-btn focusable"
          tabIndex={0}
          style={{ marginLeft: 'auto', background: 'rgba(255, 59, 48, 0.1)', color: '#ff3b30' }}
          onClick={handleLogout}
        >
          Cerrar Sesión
        </button>
      </div>

          {activeTab === 'list' && (
        <div className="glass-panel form-card">
          <h3 className="section-title">Gestión de Fuentes (Agrupado por Proveedor/Página)</h3>
          <p className="text-muted" style={{ marginBottom: '16px' }}>
            Aquí puedes ver todos los elementos agrupados por su fuente de origen. Si eliminas una fuente, se borrarán todos los elementos que contiene de forma automática.
          </p>

          <form onSubmit={handleAddCategory} style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', marginBottom: '24px' }}>
            <input 
              type="text" 
              className="form-input" 
              placeholder="Nombre de la nueva fuente vacía (Ej: Mis Videos)"
              value={newCategoryName}
              onChange={(e) => setNewCategoryName(e.target.value)}
              style={{ flexGrow: 1, minWidth: '200px' }}
            />
            <select
              className="form-select"
              value={newCategoryType}
              onChange={(e) => setNewCategoryType(e.target.value)}
              style={{ width: '220px' }}
            >
              <option value="movie">Películas (Cine)</option>
              <option value="tv">Canales en Vivo (TV)</option>
              <option value="series">Series de TV</option>
              <option value="anime_movie">Anime (Películas)</option>
              <option value="anime_series">Anime (Series)</option>
            </select>
            <button type="submit" className="btn btn-primary">
              <Plus size={16} style={{ marginRight: '6px', verticalAlign: 'middle' }} />
              Crear Fuente Vacía
            </button>
          </form>

          <div className="admin-table-container">
            {categoriesDetailed.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '20px' }}>
                <p className="text-muted" style={{ marginBottom: '16px' }}>No hay fuentes cargadas aún.</p>
                <button className="btn btn-secondary focusable" tabIndex={0} onClick={() => setActiveTab('add')}>
                  <PlusCircle size={16} style={{ marginRight: '6px' }} />Añadir primera fuente
                </button>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                {[
                  { id: 'movie', label: '🎥 Películas (Cine)', color: '#3b82f6' },
                  { id: 'series', label: '🎬 Series de TV', color: '#10b981' },
                  { id: 'tv', label: '📺 Canales en Vivo (TV)', color: '#a855f7' },
                  { id: 'anime', label: '🌸 Anime', color: '#ec4899', types: ['anime_movie', 'anime_series'] },
                ].map(sec => {
                  const catsInSec = categoriesDetailed.filter(c => {
                    if (sec.types) return sec.types.includes(c.type);
                    return c.type === sec.id;
                  });

                  if (catsInSec.length === 0) return null;

                  return (
                    <div key={sec.id} style={{ borderLeft: `4px solid ${sec.color}`, paddingLeft: '12px', marginBottom: '10px' }}>
                      <h4 style={{ color: sec.color, margin: '0 0 12px 0', fontSize: '1.1rem', fontWeight: 'bold' }}>{sec.label}</h4>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                        {catsInSec.map((cat, i) => {
                          const itemsInCat = sources.filter(s => s.category === cat.name);
                          const isExpanded = expandedCategory === cat.name;
                          return (
                            <div key={i} style={{ background: 'rgba(255,255,255,0.03)', borderRadius: '8px', padding: '12px', border: '1px solid rgba(255,255,255,0.05)' }}>
                              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '10px' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                                  <h5 style={{ margin: 0, color: '#fff', fontSize: '1rem' }}>{cat.name}</h5>
                                  <span className="card-badge" style={{ background: 'rgba(255,255,255,0.1)', color: '#ccc' }}>{itemsInCat.length} elementos</span>
                                  <span className="card-badge" style={{ background: `${sec.color}22`, color: sec.color, border: `1px solid ${sec.color}44` }}>
                                    {cat.type === 'anime_movie' ? 'Anime (Cine)' : cat.type === 'anime_series' ? 'Anime (Serie)' : cat.type === 'movie' ? 'Cine' : cat.type === 'tv' ? 'TV' : 'Serie'}
                                  </span>
                                </div>
                                <div style={{ display: 'flex', gap: '10px' }}>
                                  <button className="btn btn-secondary focusable" tabIndex={0} style={{ padding: '6px 12px', fontSize: '0.8rem' }} onClick={() => setExpandedCategory(isExpanded ? null : cat.name)}>
                                    {isExpanded ? 'Ocultar Elementos' : 'Ver Elementos'}
                                  </button>
                                  <button className="btn delete focusable" tabIndex={0} style={{ padding: '6px 12px', fontSize: '0.8rem', background: 'rgba(255, 59, 48, 0.1)', color: '#ff3b30', border: '1px solid rgba(255, 59, 48, 0.2)' }} onClick={() => handleDeleteCategory(cat.name)}>
                                    <Trash2 size={14} style={{ marginRight: '4px', verticalAlign: 'middle' }}/> Eliminar Fuente
                                  </button>
                                </div>
                              </div>
                              
                              {isExpanded && (
                                <div style={{ marginTop: '16px' }}>
                                  {itemsInCat.length === 0 ? (
                                    <p className="text-muted" style={{ fontSize: '0.9rem', margin: 0 }}>Esta fuente está vacía.</p>
                                  ) : (
                                    <table className="admin-table" style={{ fontSize: '0.9rem' }}>
                                      <thead>
                                        <tr>
                                          <th>Poster</th>
                                          <th>Título</th>
                                          <th>Tipo</th>
                                          <th>Servidores</th>
                                          <th>Acciones</th>
                                        </tr>
                                      </thead>
                                      <tbody>
                                        {itemsInCat.map((item) => (
                                          <tr key={item.id}>
                                            <td>
                                              <img src={item.poster} alt="" className="admin-thumb" style={{ width: '40px', height: '60px', objectFit: 'cover', borderRadius: '4px' }} />
                                            </td>
                                            <td style={{ fontWeight: 600, color: '#fff' }}>{item.title}</td>
                                            <td>
                                              <span className={`card-badge ${item.type}`}>
                                                {item.type === 'tv' ? 'TV' : item.type === 'series' ? 'Serie' : item.type === 'movie' ? 'Cine' : item.type === 'anime_movie' ? 'Anime Cine' : 'Anime Serie'}
                                              </span>
                                            </td>
                                            <td>{item.streams ? item.streams.length : 0}</td>
                                            <td>
                                              <div style={{ display: 'flex', gap: '10px' }}>
                                                <button className="action-btn" onClick={() => handleEditSource(item)} title="Editar">
                                                  <Edit2 size={16} />
                                                </button>
                                                <button className="action-btn delete" onClick={() => handleDeleteSource(item.id)} title="Eliminar">
                                                  <Trash2 size={16} />
                                                </button>
                                              </div>
                                            </td>
                                          </tr>
                                        ))}
                                      </tbody>
                                    </table>
                                  )}
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {activeTab === 'add' && (
        <div className="admin-container">
          {/* Main Form */}
          <div className="glass-panel form-card">
            <h3 className="section-title">{editingId ? 'Editar Elemento' : 'Añadir Elemento a una Fuente'}</h3>
            <p className="text-muted" style={{ marginBottom: '16px' }}>
              Los elementos se agrupan en Fuentes (Categorías). Selecciona a qué fuente pertenecerá este elemento o crea una nueva desde la pestaña "Gestión de Fuentes".
            </p>
            <form onSubmit={handleSubmitSource}>
              <div className="form-row" style={{ marginBottom: '16px' }}>
                <div className="form-group">
                  <label className="form-label">Tipo de Contenido</label>
                  <select 
                    className="form-select" 
                    value={type} 
                    onChange={(e) => setType(e.target.value)}
                  >
                    <option value="movie">Película</option>
                    <option value="tv">Canal de TV</option>
                    <option value="series">Serie de TV</option>
                    <option value="anime_movie">Anime (Película)</option>
                    <option value="anime_series">Anime (Serie)</option>
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label">Fuente / Categoría Destino</label>
                  <select 
                    className="form-select" 
                    value={category} 
                    onChange={(e) => setCategory(e.target.value)}
                  >
                    {categoriesDetailed.filter(cat => {
                      if (editingId && cat.name === category) return true;
                      return cat.type === type;
                    }).length === 0 ? (
                      <option value="">No hay fuentes de este tipo</option>
                    ) : (
                      categoriesDetailed.filter(cat => {
                        if (editingId && cat.name === category) return true;
                        return cat.type === type;
                      }).map((cat, i) => (
                        <option key={i} value={cat.name}>{cat.name}</option>
                      ))
                    )}
                  </select>
                  {categoriesDetailed.filter(c => c.type === type).length === 0 && (
                    <p style={{ color: '#ff453a', fontSize: '0.8rem', marginTop: '4px', margin: 0 }}>
                      ⚠️ No hay ninguna fuente para este tipo. Ve a la pestaña "Gestión de Fuentes" para crear una.
                    </p>
                  )}
                </div>
              </div>

              {(type === 'movie' || type === 'series') && (
                <div className="form-group" style={{ marginBottom: '16px' }}>
                  <label className="form-label">TMDB ID (Para autocompletar)</label>
                  <div style={{ display: 'flex', gap: '10px' }}>
                    <input 
                      type="text" 
                      className="form-input" 
                      placeholder="Ej: 299534" 
                      value={tmdbId} 
                      onChange={(e) => setTmdbId(e.target.value)}
                      style={{ flexGrow: 1 }}
                    />
                    <button 
                      type="button" 
                      className="btn btn-secondary" 
                      onClick={handleAutoFillTMDB}
                    >
                      Autocompletar
                    </button>
                  </div>
                  <p className="text-muted" style={{ fontSize: '0.75rem', marginTop: '4px' }}>
                    Busca tu película en themoviedb.org y copia el ID numérico de la URL para rellenar portada, título y detalles.
                  </p>
                </div>
              )}

              <div className="form-group" style={{ marginBottom: '16px' }}>
                <label className="form-label">Título</label>
                <input 
                  type="text" 
                  className="form-input" 
                  placeholder="Nombre de la película o canal" 
                  value={title} 
                  onChange={(e) => setTitle(e.target.value)}
                  required
                />
              </div>

              <div className="form-group" style={{ marginBottom: '16px' }}>
                <label className="form-label">URL del Póster/Logo (Imagen)</label>
                <input 
                  type="url" 
                  className="form-input" 
                  placeholder="https://ejemplo.com/poster.jpg" 
                  value={poster} 
                  onChange={(e) => setPoster(e.target.value)}
                />
              </div>

              <div className="form-group" style={{ marginBottom: '24px' }}>
                <label className="form-label">Sinopsis / Descripción</label>
                <textarea 
                  className="form-textarea" 
                  placeholder="Escribe una breve descripción del contenido..." 
                  value={description} 
                  onChange={(e) => setDescription(e.target.value)}
                />
              </div>

              {/* Streams List Subform */}
              <div style={{ marginBottom: '30px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                  <label className="form-label" style={{ margin: 0 }}>Servidores y Enlaces de Transmisión</label>
                  <button 
                    type="button" 
                    className="btn btn-secondary" 
                    onClick={addStreamField}
                    style={{ padding: '6px 12px', fontSize: '0.8rem' }}
                  >
                    <Plus size={14} /> Añadir Servidor
                  </button>
                </div>

                {streams.map((stream, idx) => (
                  <div key={idx} className="stream-item">
                    <input 
                      type="text" 
                      className="form-input" 
                      placeholder="Nombre (ej: Servidor Latino)" 
                      value={stream.name} 
                      onChange={(e) => handleStreamChange(idx, 'name', e.target.value)}
                      style={{ width: '130px' }}
                    />
                    
                    <select
                      className="form-select"
                      value={stream.resolver}
                      onChange={(e) => handleStreamChange(idx, 'resolver', e.target.value)}
                      style={{ width: '130px' }}
                    >
                      <option value="direct">Directo (HLS/MP4)</option>
                      <option value="vidsrc">VidSrc API (Auto)</option>
                      <option value="iframe">Iframe Protegido</option>
                    </select>

                    <input 
                      type="text" 
                      className="form-input" 
                      placeholder={stream.resolver === 'vidsrc' ? 'Enlace automático (no requiere URL)' : 'https://ejemplo.com/video.mp4 o .m3u8'} 
                      value={stream.url} 
                      onChange={(e) => handleStreamChange(idx, 'url', e.target.value)}
                      disabled={stream.resolver === 'vidsrc'}
                      style={{ flexGrow: 1 }}
                    />

                    {streams.length > 1 && (
                      <button 
                        type="button" 
                        className="action-btn delete" 
                        onClick={() => removeStreamField(idx)}
                      >
                        <Trash2 size={16} />
                      </button>
                    )}
                  </div>
                ))}
              </div>

              <div style={{ display: 'flex', gap: '12px' }}>
                <button type="submit" className="btn btn-primary" style={{ flexGrow: 1 }}>
                  {editingId ? 'Guardar Cambios' : 'Añadir a la Biblioteca'}
                </button>
                <button type="button" className="btn btn-secondary" onClick={resetSourceForm}>
                  Cancelar
                </button>
              </div>
            </form>
          </div>

          {/* Form Side Preview */}
          <div className="glass-panel form-card" style={{ height: 'fit-content' }}>
            <h3 className="section-title">Previsualización del Card</h3>
            <div style={{ display: 'flex', justifyContent: 'center', padding: '20px 0' }}>
              <div className="card" style={{ width: '220px' }}>
                <div className="card-img-wrapper">
                  <span className={`card-badge ${type}`}>
                    {type === 'tv' ? 'TV' : type === 'series' ? 'Serie' : 'Cine'}
                  </span>
                  <img 
                    src={poster || 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=400'} 
                    alt="Poster preview" 
                    className="card-img" 
                  />
                  <div className="card-overlay"></div>
                </div>
                <div className="card-info">
                  <h3 className="card-title">{title || 'Título del Contenido'}</h3>
                  <p className="card-category">{category || 'Categoría'}</p>
                </div>
              </div>
            </div>
            <div className="text-muted" style={{ fontSize: '0.85rem' }}>
              <h4 style={{ color: '#fff', marginBottom: '6px' }}>Consejos:</h4>
              <p style={{ marginBottom: '6px' }}>• Para canales IPTV usa <strong>Directo (HLS/MP4)</strong> con un enlace que termine en <code>.m3u8</code>.</p>
              <p>• Si usas reproductores externos con anuncios, cárgalos en modo <strong>Iframe Protegido</strong> para aislarlos.</p>
            </div>
          </div>
        </div>
      )}

      {/* Removing the Categories tab since it is integrated into the List tab */}

      {activeTab === 'm3u' && (
        <div className="glass-panel form-card">
          <h3 className="section-title">Importar Fuente M3U (IPTV masivo)</h3>
          <p className="text-muted" style={{ marginBottom: '16px' }}>
            Importa múltiples canales de TV de un solo golpe. Todos los elementos se agruparán dentro de la Fuente que indiques abajo.
          </p>

          <form onSubmit={handleImportM3U}>
            <div className="form-row" style={{ marginBottom: '16px' }}>
              <div className="form-group">
                <label className="form-label">Nombre de la Fuente Destino</label>
                <input 
                  type="text" 
                  className="form-input" 
                  value={m3uCategory} 
                  onChange={(e) => setM3uCategory(e.target.value)}
                  placeholder="Ej: IPTV Latino"
                />
              </div>
              <div className="form-group">
                <label className="form-label">Tipo de Contenido de la Lista</label>
                <select 
                  className="form-select" 
                  value={m3uType} 
                  onChange={(e) => setM3uType(e.target.value)}
                >
                  <option value="tv">Canales de TV en Vivo</option>
                  <option value="movie">Películas (Cine)</option>
                  <option value="series">Series de TV</option>
                  <option value="anime_movie">Anime (Películas)</option>
                  <option value="anime_series">Anime (Series)</option>
                </select>
              </div>
            </div>
            <div style={{ marginBottom: '16px' }}>
              <p className="text-muted" style={{ fontSize: '0.75rem', marginTop: '-8px' }}>
                Si la lista contiene la etiqueta <code>group-title="..."</code>, se intentará usar el grupo original del canal en lugar de esta fuente por defecto.
              </p>
            </div>

            <div className="form-group" style={{ marginBottom: '16px' }}>
              <label className="form-label">URL Remota de Lista M3U (.m3u o .m3u8)</label>
              <input 
                type="url" 
                className="form-input" 
                value={m3uUrl} 
                onChange={(e) => setM3uUrl(e.target.value)}
                placeholder="https://proveedor-iptv.com/lista.m3u"
                disabled={m3uRaw.trim() !== ''}
              />
            </div>

            <div style={{ textAlign: 'center', margin: '14px 0', color: 'var(--text-muted)', fontWeight: 600 }}>
              Ó PEGAR CONTENIDO DE LA LISTA
            </div>

            <div className="form-group" style={{ marginBottom: '24px' }}>
              <label className="form-label">Texto M3U Raw</label>
              <textarea 
                className="form-textarea" 
                value={m3uRaw} 
                onChange={(e) => setM3uRaw(e.target.value)}
                placeholder="#EXTM3U&#10;#EXTINF:-1 tvg-logo='http://logo.com' group-title='Noticias',Canal Informativo&#10;http://stream.m3u8"
                disabled={m3uUrl.trim() !== ''}
                style={{ minHeight: '180px', fontFamily: 'monospace', fontSize: '0.85rem' }}
              />
            </div>

            <button type="submit" className="btn btn-primary" style={{ width: '100%' }}>
              Comenzar Importación
            </button>
          </form>
        </div>
      )}

      {activeTab === 'discovery' && (
        <div className="glass-panel form-card">
          <h3 className="section-title">Sincronización y Autodescubrimiento con Brave Search</h3>
          <p className="text-muted" style={{ marginBottom: '24px' }}>
            Vision+ puede rastrear automáticamente la web pública a través de Brave Search para encontrar listas M3U y transmisiones libres VOD/IPTV actualizadas. Al iniciar un proceso, este se ejecutará en segundo plano en el servidor y actualizará la base de datos de inmediato.
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
            {/* Box for Live TV */}
            <div className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '12px', background: 'rgba(255,255,255,0.02)', border: '1px solid var(--border-color)', borderRadius: 'var(--radius-md)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Tv size={28} style={{ color: 'var(--primary-light)' }} />
                <h4 style={{ color: '#fff', fontSize: '1.2rem', margin: 0, fontWeight: 700 }}>Canales de TV en Vivo</h4>
              </div>
              <p className="text-secondary" style={{ fontSize: '0.9rem', flexGrow: 1, margin: 0, lineHeight: 1.5 }}>
                Rastrea playlists públicas de IPTV LATAM (México, Argentina, Colombia, Chile, Perú) y temáticas en Brave Search.
              </p>
              <button 
                type="button" 
                className="btn btn-primary focusable"
                style={{ width: '100%', marginTop: '8px' }}
                onClick={() => {
                  showAlert('info', 'Iniciando descubrimiento de canales de TV...');
                  fetch('/api/channels/refresh', { method: 'POST' })
                    .then(res => res.json())
                    .then(data => {
                      showAlert('success', data.message || 'Proceso iniciado con éxito.');
                    })
                    .catch(err => {
                      showAlert('error', 'Error al conectar con el servidor: ' + err.message);
                    });
                }}
              >
                Sincronizar Canales
              </button>
              <button 
                type="button" 
                className="btn btn-secondary focusable"
                style={{ width: '100%', marginTop: '8px', background: 'rgba(255,204,0,0.1)', color: '#ffcc00', borderColor: '#ffcc00' }}
                onClick={() => {
                  showAlert('info', 'Importando canales de Pluto TV en vivo (México)...');
                  fetch('/api/seed-plutotv-live', { method: 'POST' })
                    .then(res => res.json())
                    .then(data => {
                      if (data.success) {
                        showAlert('success', `¡Importación exitosa! Se agregaron ${data.added} canales de Pluto TV.`);
                      } else {
                        showAlert('error', data.error || data.message || 'Error en la importación.');
                      }
                    })
                    .catch(err => {
                      showAlert('error', 'Error al conectar con el servidor: ' + err.message);
                    });
                }}
              >
                Importar Pluto TV (Live TV)
              </button>
              <button 
                type="button" 
                className="btn btn-secondary focusable"
                style={{ width: '100%', marginTop: '8px', background: 'rgba(168,85,247,0.1)', color: '#a855f7', borderColor: '#a855f7' }}
                onClick={() => {
                  showAlert('info', 'Importando canales de Planeta Play en vivo...');
                  fetch('/api/seed-planetaplay-live', { method: 'POST' })
                    .then(res => res.json())
                    .then(data => {
                      if (data.success) {
                        showAlert('success', `¡Importación exitosa! Se agregaron ${data.added} canales de Planeta Play.`);
                      } else {
                        showAlert('error', data.error || data.message || 'Error en la importación.');
                      }
                    })
                    .catch(err => {
                      showAlert('error', 'Error al conectar con el servidor: ' + err.message);
                    });
                }}
              >
                Importar Planeta Play (Live TV)
              </button>
            </div>

            {/* Box for VOD Movies/Series */}
            <div className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '12px', background: 'rgba(255,255,255,0.02)', border: '1px solid var(--border-color)', borderRadius: 'var(--radius-md)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Film size={28} style={{ color: 'var(--primary-light)' }} />
                <h4 style={{ color: '#fff', fontSize: '1.2rem', margin: 0, fontWeight: 700 }}>Películas y Series</h4>
              </div>
              <p className="text-secondary" style={{ fontSize: '0.9rem', flexGrow: 1, margin: 0, lineHeight: 1.5 }}>
                Importa catálogo real desde TMDB con portadas, títulos y reproducción via VidSrc. O rastrea listas VOD públicas en español.
              </p>
              <button 
                type="button" 
                className="btn btn-primary focusable"
                style={{ width: '100%', marginTop: '8px' }}
                onClick={() => {
                  showAlert('info', 'Importando catálogo de películas y series desde TMDB...');
                  fetch('/api/seed-movies', { method: 'POST' })
                    .then(res => res.json())
                    .then(data => {
                      if (data.success) {
                        showAlert('success', `¡Importación exitosa! Se agregaron ${data.added} títulos nuevos. Total en catálogo: ${data.total}.`);
                      } else {
                        showAlert('error', data.error || 'Error en la importación.');
                      }
                    })
                    .catch(err => {
                      showAlert('error', 'Error al conectar con el servidor: ' + err.message);
                    });
                }}
              >
                🎬 Importar desde TMDB (Portadas + Streaming)
              </button>
              <button 
                type="button" 
                className="btn btn-secondary focusable"
                style={{ width: '100%', marginTop: '8px' }}
                onClick={() => {
                  showAlert('info', 'Iniciando descubrimiento de películas y series...');
                  fetch('/api/movies-series/refresh', { method: 'POST' })
                    .then(res => res.json())
                    .then(data => {
                      showAlert('success', data.message || 'Proceso iniciado con éxito.');
                    })
                    .catch(err => {
                      showAlert('error', 'Error al conectar con el servidor: ' + err.message);
                    });
                }}
              >
                Sincronizar VOD (M3U Público)
              </button>
              <button 
                type="button" 
                className="btn btn-secondary focusable"
                style={{ width: '100%', marginTop: '8px', background: 'rgba(255,204,0,0.1)', color: '#ffcc00', borderColor: '#ffcc00' }}
                onClick={() => {
                  showAlert('info', 'Importando catálogo VOD de Pluto TV (México)...');
                  fetch('/api/seed-plutotv-vod', { method: 'POST' })
                    .then(res => res.json())
                    .then(data => {
                      if (data.success) {
                        showAlert('success', `¡Importación exitosa! Se agregaron ${data.added} películas de Pluto TV.`);
                      } else {
                        showAlert('error', data.error || data.message || 'Error en la importación.');
                      }
                    })
                    .catch(err => {
                      showAlert('error', 'Error al conectar con el servidor: ' + err.message);
                    });
                }}
              >
                Importar Pluto TV (VOD)
              </button>
            </div>

            {/* Box for Anime */}
            <div className="glass-panel" style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '12px', background: 'rgba(255,255,255,0.02)', border: '1px solid var(--border-color)', borderRadius: 'var(--radius-md)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Film size={28} style={{ color: '#ff3366' }} />
                <h4 style={{ color: '#fff', fontSize: '1.2rem', margin: 0, fontWeight: 700 }}>Anime</h4>
              </div>
              <p className="text-secondary" style={{ fontSize: '0.9rem', flexGrow: 1, margin: 0, lineHeight: 1.5 }}>
                Extrae películas y series de anime directamente desde servidores como AnimeFLV y JKAnime.
              </p>
              <button 
                type="button" 
                className="btn btn-primary focusable"
                style={{ width: '100%', marginTop: '8px', background: '#ff3366', borderColor: '#ff3366' }}
                onClick={() => {
                  showAlert('info', 'Sincronizando catálogo de Anime. Esto tomará varios segundos...');
                  fetch('/api/catalog/sync-anime', { method: 'POST' })
                    .then(res => res.json())
                    .then(data => {
                      showAlert('success', data.message || 'Sincronización de anime iniciada.');
                    })
                    .catch(err => {
                      showAlert('error', 'Error al conectar con el servidor: ' + err.message);
                    });
                }}
              >
                Sincronizar Catálogo de Anime
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
