import { useState, useEffect, useRef, useMemo } from 'react';
import { authApi } from '../api/auth';

export default function PasswordStrengthMeter({ password }) {
  const [serverAnalysis, setServerAnalysis] = useState(null);
  const debounceRef = useRef(null);

  // Client-side instant feedback (always available)
  const clientStrength = useMemo(() => {
    if (!password) return { score: 0, label: '', color: '', requirements: [] };

    let score = 0;
    const requirements = [];

    if (password.length >= 8) {
      score += 1;
      requirements.push({ met: true, text: '8자 이상' });
    } else {
      requirements.push({ met: false, text: '8자 이상' });
    }

    if (/[a-z]/.test(password)) {
      score += 1;
      requirements.push({ met: true, text: '소문자 포함' });
    } else {
      requirements.push({ met: false, text: '소문자 포함' });
    }

    if (/[A-Z]/.test(password)) {
      score += 1;
      requirements.push({ met: true, text: '대문자 포함' });
    } else {
      requirements.push({ met: false, text: '대문자 포함' });
    }

    if (/[0-9]/.test(password)) {
      score += 1;
      requirements.push({ met: true, text: '숫자 포함' });
    } else {
      requirements.push({ met: false, text: '숫자 포함' });
    }

    if (/[!@#$%^&*(),.?":{}|<>]/.test(password)) {
      score += 1;
      requirements.push({ met: true, text: '특수문자 포함' });
    } else {
      requirements.push({ met: false, text: '특수문자 포함' });
    }

    if (password.length >= 12) {
      score += 1;
    }

    let label, color;
    if (score <= 2) {
      label = '약함';
      color = '#dc3545';
    } else if (score <= 3) {
      label = '보통';
      color = '#ffc107';
    } else if (score <= 4) {
      label = '강함';
      color = '#28a745';
    } else {
      label = '매우 강함';
      color = '#20c997';
    }

    return { score: Math.min(score, 5), label, color, requirements };
  }, [password]);

  // Server-side detailed analysis (debounced)
  useEffect(() => {
    if (!password || password.length < 4) {
      setServerAnalysis(null); // eslint-disable-line react-hooks/set-state-in-effect
      return;
    }

    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }

    debounceRef.current = setTimeout(async () => {
      try {
        const res = await authApi.analyzePassword(password);
        setServerAnalysis(res.data);
      } catch {
        // Silently fail - client-side analysis still works
      }
    }, 500);

    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
    };
  }, [password]);

  if (!password) return null;

  // Use server analysis for display if available, otherwise fallback to client
  const displayScore = serverAnalysis ? serverAnalysis.score : (clientStrength.score / 5) * 100;
  const displayLabel = serverAnalysis ? getLevelLabel(serverAnalysis.level) : clientStrength.label;
  const displayColor = serverAnalysis ? getLevelColor(serverAnalysis.level) : clientStrength.color;

  return (
    <div className="password-strength-meter">
      <div className="strength-bar-container">
        <div
          className="strength-bar"
          style={{
            width: serverAnalysis ? `${displayScore}%` : `${(clientStrength.score / 5) * 100}%`,
            backgroundColor: displayColor
          }}
        />
      </div>
      <div className="strength-label" style={{ color: displayColor }}>
        {displayLabel}
        {serverAnalysis && (
          <span className="strength-score"> ({serverAnalysis.score}점)</span>
        )}
      </div>
      <div className="strength-requirements">
        {(serverAnalysis ? serverAnalysis.checks : clientStrength.requirements).map((item, index) => {
          const met = serverAnalysis ? item.passed : item.met;
          const text = serverAnalysis ? item.description : item.text;
          return (
            <span
              key={index}
              className={`requirement ${met ? 'met' : 'unmet'}`}
            >
              {met ? '✓' : '○'} {text}
            </span>
          );
        })}
      </div>
      {serverAnalysis && serverAnalysis.suggestions && serverAnalysis.suggestions.length > 0 && (
        <div className="strength-suggestions">
          {serverAnalysis.suggestions.map((suggestion, index) => (
            <div key={index} className="suggestion-item">
              {suggestion}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function getLevelLabel(level) {
  switch (level) {
    case 'EXCELLENT': return '매우 강함';
    case 'STRONG': return '강함';
    case 'MEDIUM': return '보통';
    case 'WEAK': return '약함';
    case 'CRITICAL': return '매우 약함';
    default: return '';
  }
}

function getLevelColor(level) {
  switch (level) {
    case 'EXCELLENT': return '#20c997';
    case 'STRONG': return '#28a745';
    case 'MEDIUM': return '#ffc107';
    case 'WEAK': return '#fd7e14';
    case 'CRITICAL': return '#dc3545';
    default: return '#6c757d';
  }
}
