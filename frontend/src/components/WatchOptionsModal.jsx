import React, { useEffect, useState } from 'react';
import { X, Tv, Globe, AlertCircle, ExternalLink, Play, Plus } from 'lucide-react';
import { getWatchOptions } from '../services/sportsApi';

export default function WatchOptionsModal({ event, onClose, onPlayChannel }) {
  const [options, setOptions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!event) return;
    setLoading(true);
    getWatchOptions(event.id)
      .then(data => {
        if (data.success) {
          setOptions(data.options || []);
        } else {
          setError(data.error || 'Error al cargar opciones');
        }
      })
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [event]);

  useEffect(() => {
    const handler = e => {
      if (e.key === 'Escape' || e.key === 'Backspace') {
        e.preventDefault();
        e.stopPropagation();
        onClose();
      }
    };
    window.addEventListener('keydown', handler, true);
    return () => window.removeEventListener('keydown', handler, true);
  }, [onClose]);

  useEffect(() => {
    setTimeout(() => {
      const first = document.querySelector('.wo-option.focusable');
      if (first) first.focus();
    }, 100);
  }, [options]);

  if (!event) return null;

  const internalOptions = options.filter(o => o.type === 'internal_channel');
  const externalOptions = options.filter(o => o.type !== 'internal_channel');

  return (
    <div className="wo-modal-overlay" onClick={onClose}>
      <div className="wo-modal" onClick={e => e.stopPropagation()}>
        <div className="wo-modal-header">
          <h2>{event.title || 'Opciones para ver'}</h2>
          <button className="wo-close focusable" onClick={onClose} tabIndex={0}>
            <X size={20} />
          </button>
        </div>

        {event.league && <p className="wo-league">{event.league}</p>}

        {loading && (
          <div className="wo-loading">
            <div className="spin-anim" style={{ width: 24, height: 24, borderWidth: 2 }} />
            <span>Buscando opciones disponibles...</span>
          </div>
        )}

        {error && (
          <div className="wo-error">
            <AlertCircle size={16} /> {error}
          </div>
        )}

        {!loading && !error && internalOptions.length === 0 && externalOptions.length === 0 && (
          <div className="wo-empty">
            <AlertCircle size={24} />
            <p>No hay opciones disponibles para este evento.</p>
            <p className="wo-hint">Puedes agregar fuentes IPTV desde el panel de administración.</p>
          </div>
        )}

        {internalOptions.length > 0 && (
          <div className="wo-section">
            <h3 className="wo-section-title"><Tv size={16} /> Canales disponibles en Vision+</h3>
            <div className="wo-list">
              {internalOptions.map((opt, i) => (
                <div
                  key={i}
                  className="wo-option focusable"
                  tabIndex={0}
                  onClick={() => onPlayChannel && onPlayChannel(opt)}
                  onKeyDown={e => { if (e.key === 'Enter' && onPlayChannel) { e.preventDefault(); onPlayChannel(opt); } }}
                >
                  <div className="wo-option-info">
                    <span className="wo-option-label">{opt.label}</span>
                    <span className="wo-option-confidence">{(opt.confidence || 0)}% coincidencia</span>
                  </div>
                  <button className="wo-play-btn" onClick={e => { e.stopPropagation(); onPlayChannel && onPlayChannel(opt); }}>
                    <Play size={16} />
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {externalOptions.length > 0 && (
          <div className="wo-section">
            <h3 className="wo-section-title"><Globe size={16} /> Otras opciones</h3>
            <div className="wo-list">
              {externalOptions.map((opt, i) => (
                <div key={i} className="wo-option focusable" tabIndex={0}>
                  <div className="wo-option-info">
                    <span className="wo-option-label">{opt.label || 'Enlace externo'}</span>
                    {opt.legalStatus && (
                      <span className="wo-legal-badge">{opt.legalStatus === 'official' ? 'Oficial' : opt.legalStatus}</span>
                    )}
                  </div>
                  {opt.url ? (
                    <a
                      href={opt.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="wo-external-link"
                      onClick={e => e.stopPropagation()}
                    >
                      <ExternalLink size={16} />
                    </a>
                  ) : (
                    <span className="wo-no-link"><AlertCircle size={14} /></span>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="wo-footer">
          <button
            className="wo-add-source focusable"
            tabIndex={0}
            onClick={() => window.location.href = '/admin'}
          >
            <Plus size={16} /> Agregar proveedor IPTV
          </button>
        </div>
      </div>
    </div>
  );
}
