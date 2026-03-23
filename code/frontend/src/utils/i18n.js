const ERROR_MAP = {
  "Failed to fetch": "网络请求失败，请确认后端已启动且可访问。",
  "request_id is required.": "缺少 request_id。",
  "Bearer token is required.": "缺少访问令牌。",
  "No static resource actuator/health.": "后端路由不存在，请检查服务是否正常启动。"
};

export function toChineseError(message, status) {
  if (!message) {
    return status ? `请求失败：${status}` : "请求失败";
  }
  if (ERROR_MAP[message]) {
    return ERROR_MAP[message];
  }
  if (message.startsWith("Request failed: ")) {
    const code = message.slice("Request failed: ".length);
    return `请求失败：${code}`;
  }
  return message;
}
