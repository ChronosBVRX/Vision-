import React, { useState, useEffect } from 'react';
import { X } from 'lucide-react';

const TRUST_MESSAGES = [
  "Estableciendo canal cifrado ultra rápido...",
  "Evadiendo anuncios y rastreadores de origen...",
  "Analizando servidores para omitir publicidad...",
  "Verificando códecs de video en alta definición...",
  "Optimizando búfer para una transmisión fluida...",
  "Sintonizando la señal de manera directa...",
  "Conectando con el reproductor premium..."
];

export default function LoadingScreen({ 
  type = 'catalog', 
  title, 
  onCancel, 
  onClose,
  closeButtonRef,
  theme
}) {
  const [messageIdx, setMessageIdx] = useState(0);

  useEffect(() => {
    if (type !== 'resolver') return;
    
    const interval = setInterval(() => {
      setMessageIdx((prev) => (prev + 1) % TRUST_MESSAGES.length);
    }, 3000); // Cycles every 3 seconds to match the CSS animation cycle

    return () => clearInterval(interval);
  }, [type]);

  const containerClass = `vision-loading-container ${
    type === 'resolver' ? 'resolver-mode' : type === 'player' ? 'player-mode' : ''
  }`;

  return (
    <div className={containerClass}>
      {/* Horizontal glowing bar at the top */}
      <div className="vision-loading-line-bar" />

      {/* Close button for player loading overlay */}
      {type === 'player' && onClose && (
        <div className="watch-header" style={{ position: 'absolute', top: 0, left: 0, width: '100%', pointerEvents: 'none' }}>
          <div className="watch-title">{title || 'Vision+'}</div>
          <button 
            ref={closeButtonRef} 
            className="watch-close focusable" 
            tabIndex={0} 
            onClick={onClose} 
            title="Cerrar Reproductor" 
            style={{ pointerEvents: 'auto' }}
          >
            <X size={20} />
          </button>
        </div>
      )}

      <div className="vision-loading-card">
        {/* Animated Double-Ring Spinner */}
        <div className="vision-spinner-wrapper">
          <div className="vision-spinner-outer" />
          <div className="vision-spinner-inner" />
          <span className="vision-spinner-logo">V+</span>
        </div>

        {/* Title */}
        <h3 className="vision-loading-title">
          {title || (type === 'catalog' ? 'Cargando contenido...' : 'Cargando...')}
        </h3>

        {/* Dynamic Trust Messaging */}
        {type === 'resolver' && (
          <>
            <div className="vision-loading-subtitle">
              {' Sintonizando Señal'}
            </div>
            <div className="vision-loading-trust-box">
              <span className="vision-loading-trust-text">
                <span className="vision-loading-trust-icon">✓</span>
                {TRUST_MESSAGES[messageIdx]}
              </span>
            </div>
            {onCancel && (
              <button 
                className="vision-loading-cancel-btn focusable" 
                tabIndex={0} 
                onClick={onCancel}
              >
                Cancelar
              </button>
            )}
          </>
        )}

        {/* Subtitle for page catalog loaders */}
        {type === 'catalog' && (
          <p className="text-secondary" style={{ fontSize: '0.9rem', opacity: 0.8, marginTop: '5px' }}>
            Obteniendo cartelera y sincronizando cartelera premium...
          </p>
        )}

        {/* Subtitle for player internal buffer loader */}
        {type === 'player' && (
          <>
            <div className="vision-loading-subtitle" style={{ fontSize: '0.8rem', letterSpacing: '1px' }}>
              Optimizando búfer de video
            </div>
            <p className="text-secondary" style={{ fontSize: '0.85rem', opacity: 0.85 }}>
              Evadiendo protección antibots del proveedor y buscando señal limpia
            </p>
          </>
        )}
      </div>
    </div>
  );
}
