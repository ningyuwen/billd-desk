function fit(width, height, frameWidth, frameHeight) {
  const scale = Math.min(width / frameWidth, height / frameHeight);
  const w = frameWidth * scale;
  const h = frameHeight * scale;
  return { x: (width - w) / 2, y: (height - h) / 2, width: w, height: h };
}
function point(x, y, rectangle) {
  if (
    !rectangle ||
    x < rectangle.x ||
    y < rectangle.y ||
    x > rectangle.x + rectangle.width ||
    y > rectangle.y + rectangle.height
  )
    return null;
  return {
    x: (x - rectangle.x) / rectangle.width,
    y: (y - rectangle.y) / rectangle.height,
  };
}
module.exports = { fit, point };
