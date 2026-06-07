import React from 'react';
import { Clock, Trophy, Tv, Zap, AlertCircle } from 'lucide-react';

function formatEventTime(utcStr) {
  if (!utcStr) return '';
  try {
    const d = new Date(utcStr);
    if (isNaN(d.getTime())) return utcStr;
    return d.toLocaleString('es-MX', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  } catch {
    return utcStr;
  }
}

function getStatusLabel(status) {
  if (!status || status === 'scheduled') return 'Próximamente';
  if (status === 'live') return 'EN VIVO';
  if (status === 'finished') return 'Finalizado';
  return status;
}

function getStatusClass(status) {
  if (status === 'live') return 'sports-status-live';
  if (status === 'finished') return 'sports-status-finished';
  return 'sports-status-scheduled';
}

function getSportIcon(sport) {
  const s = (sport || '').toLowerCase();
  if (s.includes('soccer') || s.includes('football') || s.includes('futbol')) return '⚽';
  if (s.includes('basketball')) return '🏀';
  if (s.includes('baseball')) return '⚾';
  if (s.includes('tennis')) return '🎾';
  if (s.includes('boxing') || s.includes('mma')) return '🥊';
  if (s.includes('motorsport') || s.includes('formula') || s.includes('f1')) return '🏎️';
  if (s.includes('american')) return '🏈';
  return '🏆';
}

export default function SportsEventCard({ event, onWatchOptions, isTVMode }) {
  const isLive = event.status === 'live';
  const isSoon = !isLive && event.startTime && new Date(event.startTime).getTime() - Date.now() < 2 * 60 * 60 * 1000;

  return (
    <div
      className={`sports-event-card focusable ${isLive ? 'sports-live' : ''} ${isSoon ? 'sports-soon' : ''}`}
      tabIndex={0}
      role="button"
      onClick={() => onWatchOptions && onWatchOptions(event)}
      onKeyDown={e => { if ((e.key === 'Enter' || e.key === ' ') && onWatchOptions) { e.preventDefault(); onWatchOptions(event); } }}
    >
      <div className="sports-event-header">
        <span className="sports-event-sport">
          {getSportIcon(event.sport)} {event.sport || 'Deporte'}
        </span>
        <span className={`sports-event-status ${getStatusClass(event.status)}`}>
          {isLive && <Zap size={12} style={{ marginRight: 3 }} />}
          {getStatusLabel(event.status)}
        </span>
      </div>

      <div className="sports-event-teams">
        <div className="sports-team">
          {event.homeTeamBadge && (
            <img src={event.homeTeamBadge} alt="" className="sports-team-badge" onError={e => e.target.style.display = 'none'} />
          )}
          <span className="sports-team-name">{event.homeTeam || 'Equipo Local'}</span>
        </div>
        <span className="sports-vs">VS</span>
        <div className="sports-team">
          {event.awayTeamBadge && (
            <img src={event.awayTeamBadge} alt="" className="sports-team-badge" onError={e => e.target.style.display = 'none'} />
          )}
          <span className="sports-team-name">{event.awayTeam || 'Equipo Visitante'}</span>
        </div>
      </div>

      <div className="sports-event-meta">
        {event.league && (
          <span className="sports-league"><Trophy size={12} /> {event.league}</span>
        )}
        <span className="sports-time"><Clock size={12} /> {formatEventTime(event.startTime)}</span>
      </div>

      {event.channels && event.channels.length > 0 && (
        <div className="sports-channels">
          <Tv size={12} />
          {event.channels.slice(0, 3).map((ch, i) => (
            <span key={i} className="sports-channel-tag">{ch.channelName}</span>
          ))}
          {event.channels.length > 3 && (
            <span className="sports-channel-more">+{event.channels.length - 3}</span>
          )}
        </div>
      )}

      {event.channels && event.channels.length === 0 && (
        <div className="sports-no-channels">
          <AlertCircle size={12} /> Sin canales detectados
        </div>
      )}

      {event.interestScore > 0 && (
        <div className="sports-interest" title="Interés">
          <Zap size={12} /> {event.interestScore}
        </div>
      )}

      {onWatchOptions && (
        <button
          className="sports-watch-btn"
          onClick={e => { e.stopPropagation(); onWatchOptions(event); }}
        >
          {isLive ? 'Ver ahora' : 'Ver opciones'}
        </button>
      )}
    </div>
  );
}

export { formatEventTime, getStatusLabel, getSportIcon };
