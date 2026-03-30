export function resolveSelectableCodeUpdate(options, currentCode) {
  const normalizedOptions = Array.isArray(options) ? options : [];
  if (normalizedOptions.length === 0) {
    return currentCode ? "" : null;
  }
  const exists = normalizedOptions.some((item) => item.code === currentCode);
  if (exists) {
    return null;
  }
  return normalizedOptions[0].code;
}
