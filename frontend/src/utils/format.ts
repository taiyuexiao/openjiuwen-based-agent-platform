/** JSON 文本解析：空文本返回 undefined，非法 JSON 抛出带中文信息的 Error */
export function parseJson(text?: string): unknown {
  if (text === undefined || text === null || text.trim() === '') {
    return undefined;
  }
  try {
    return JSON.parse(text);
  } catch {
    throw new Error('JSON 格式不合法，请检查输入');
  }
}

/** 实体中的 JSON 字符串字段美化展示；解析失败则原样返回 */
export function prettyJson(value?: string | null): string {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

/** 后端 LocalDateTime（ISO 字符串）展示 */
export function fmtTime(value?: string | null): string {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
}

/**  epoch 毫秒/微秒展示（trace startTime/endTime 为 long，按毫秒处理，过大则截断原样显示） */
export function fmtEpoch(value?: number): string {
  if (value === undefined || value === null) {
    return '-';
  }
  const ms = value > 1e15 ? Math.floor(value / 1000) : value;
  if (ms > 1e12) {
    return new Date(ms).toLocaleString();
  }
  return String(value);
}
