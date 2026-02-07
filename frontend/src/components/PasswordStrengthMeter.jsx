import { useMemo } from 'react';

export default function PasswordStrengthMeter({ password }) {
  const strength = useMemo(() => {
    if (!password) return { score: 0, label: '', color: '', requirements: [] };

    let score = 0;
    const requirements = [];

    // Length check
    if (password.length >= 8) {
      score += 1;
      requirements.push({ met: true, text: '8자 이상' });
    } else {
      requirements.push({ met: false, text: '8자 이상' });
    }

    // Lowercase check
    if (/[a-z]/.test(password)) {
      score += 1;
      requirements.push({ met: true, text: '소문자 포함' });
    } else {
      requirements.push({ met: false, text: '소문자 포함' });
    }

    // Uppercase check
    if (/[A-Z]/.test(password)) {
      score += 1;
      requirements.push({ met: true, text: '대문자 포함' });
    } else {
      requirements.push({ met: false, text: '대문자 포함' });
    }

    // Number check
    if (/[0-9]/.test(password)) {
      score += 1;
      requirements.push({ met: true, text: '숫자 포함' });
    } else {
      requirements.push({ met: false, text: '숫자 포함' });
    }

    // Special character check
    if (/[!@#$%^&*(),.?":{}|<>]/.test(password)) {
      score += 1;
      requirements.push({ met: true, text: '특수문자 포함' });
    } else {
      requirements.push({ met: false, text: '특수문자 포함' });
    }

    // Bonus for length
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

  if (!password) return null;

  return (
    <div className="password-strength-meter">
      <div className="strength-bar-container">
        <div
          className="strength-bar"
          style={{
            width: `${(strength.score / 5) * 100}%`,
            backgroundColor: strength.color
          }}
        />
      </div>
      <div className="strength-label" style={{ color: strength.color }}>
        {strength.label}
      </div>
      <div className="strength-requirements">
        {strength.requirements.map((req, index) => (
          <span
            key={index}
            className={`requirement ${req.met ? 'met' : 'unmet'}`}
          >
            {req.met ? '✓' : '○'} {req.text}
          </span>
        ))}
      </div>
    </div>
  );
}
