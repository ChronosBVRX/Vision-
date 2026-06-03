import React, { useState, useEffect } from 'react';

export default function TrailerPlayer({ item, isPlaying }) {
  const [trailerId, setTrailerId] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!item) return;
    let mounted = true;
    setLoading(true);
    setTrailerId(null);
    
    // In a real app we would use TMDB to fetch the Youtube ID.
    // Here we will use a generic cool background video, or if we can,
    // we can use a free scraper endpoint. For demo purposes and reliability 
    // on Smart TVs without a real TMDB key, we'll use a reliable list of IDs
    // based on genre, or fallback to a cinematic reel.
    
    const cinematicReels = [
      "h3LDP3F81c8", // Netflix Intro/Cinematic
      "ScMzIvxBSi4", // 4K Space
      "qC0vDKVPCrw", // Cinematic Actions
    ];
    
    // Fallback logic
    setTimeout(() => {
      if (mounted) {
        setTrailerId(cinematicReels[Math.floor(Math.random() * cinematicReels.length)]);
        setLoading(false);
      }
    }, 500);

    return () => { mounted = false; };
  }, [item]);

  if (!isPlaying || loading || !trailerId) return null;

  return (
    <div className="trailer-wrapper">
      <iframe
        className="trailer-iframe"
        src={`https://www.youtube.com/embed/${trailerId}?autoplay=1&mute=1&controls=0&modestbranding=1&showinfo=0&rel=0&loop=1&playlist=${trailerId}`}
        allow="autoplay; encrypted-media"
        allowFullScreen
        frameBorder="0"
      ></iframe>
      <div className="trailer-overlay"></div>
    </div>
  );
}
