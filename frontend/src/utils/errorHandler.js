/**
 * API 에러에서 사용자 친화적 메시지를 추출
 */
export function getErrorMessage(error) {
  // 서버 응답이 있는 경우
  if (error.response?.data?.message) {
    return error.response.data.message;
  }

  if (error.response?.data?.error) {
    return error.response.data.error;
  }

  // HTTP 상태 코드별 기본 메시지
  if (error.response) {
    const status = error.response.status;
    switch (status) {
      case 400: return '잘못된 요청입니다.';
      case 401: return '인증이 필요합니다. 다시 로그인해주세요.';
      case 403: return '접근 권한이 없습니다.';
      case 404: return '요청한 리소스를 찾을 수 없습니다.';
      case 409: return '이미 존재하는 데이터입니다.';
      case 429: return '요청이 너무 많습니다. 잠시 후 다시 시도해주세요.';
      case 500: return '서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
      case 502: return '서버에 연결할 수 없습니다.';
      case 503: return '서비스가 일시적으로 이용 불가합니다.';
      default: return `오류가 발생했습니다. (${status})`;
    }
  }

  // 네트워크 에러
  if (error.code === 'ERR_NETWORK' || !error.response) {
    return '네트워크 연결을 확인해주세요.';
  }

  // 타임아웃
  if (error.code === 'ECONNABORTED') {
    return '요청 시간이 초과되었습니다. 다시 시도해주세요.';
  }

  return error.message || '알 수 없는 오류가 발생했습니다.';
}

/**
 * Rate Limit 에러에서 남은 시간을 추출
 */
export function getRateLimitRetryAfter(error) {
  if (error.response?.status === 429) {
    const retryAfter = error.response.headers?.['retry-after'];
    return retryAfter ? parseInt(retryAfter, 10) : 60;
  }
  return null;
}
