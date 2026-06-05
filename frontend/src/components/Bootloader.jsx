import React, { useEffect, useState } from 'react';
import logoImg from '../assets/logo.png';
import { useCatalog } from '../context/CatalogContext';

export default function Bootloader({ children }) {
  const { loading } = useCatalog();
  const [show, setShow] = useState(true);
  const [fade, setFade] = useState(false);
  const version = import.meta.env.VITE_APP_VERSION || '1.0.0';

  useEffect(() => {
    // Definimos el tiempo de inicio
    const startTime = Date.now();
    // Mínimo 2 segundos de pantalla de carga para que no parpadee bruscamente
    const minLoadTime = 2000; 

    // Solo empezamos a quitarlo si no está cargando datos
    if (!loading) {
      const elapsedTime = Date.now() - startTime;
      const timeToWait = Math.max(0, minLoadTime - elapsedTime);

      const timer = setTimeout(() => {
        setFade(true);
        // Luego de la animación de fade, ocultar el DOM del bootloader
        setTimeout(() => setShow(false), 800); 
      }, timeToWait);

      return () => clearTimeout(timer);
    }
  }, [loading]);

  return (
    <>
      {show && (
        <div className={`bootloader-overlay ${fade ? 'fade-out' : ''}`}>
          <div className="bootloader-content">
            <img src={logoImg} alt="Vision+" className="bootloader-logo" />
            
            {/* Animación de carga sutil */}
            <div className="bootloader-loader">
              <div className="bootloader-dot"></div>
              <div className="bootloader-dot"></div>
              <div className="bootloader-dot"></div>
            </div>

            <span className="bootloader-version">v{version}</span>
          </div>
        </div>
      )}
      
      {/* Mantenemos children montado detrás del bootloader para que React y el navegador
          vayan cargando las imágenes y el CSS del Home en segundo plano */}
      <div 
        className="app-root-wrapper"
        style={{ 
          opacity: show ? (fade ? 1 : 0) : 1, 
          transition: 'opacity 0.8s ease-in-out',
          height: '100%',
          width: '100%'
        }}
      >
        {children}
      </div>
    </>
  );
}
