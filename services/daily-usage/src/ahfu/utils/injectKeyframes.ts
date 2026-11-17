export function injectKeyframes(id: string, css: string): void {
  if (typeof document === 'undefined') return;
  const existing = document.querySelector(`style[data-ahfu-keyframes="${id}"]`);
  if (existing) {
    existing.textContent = css;
    return;
  }
  const style = document.createElement('style');
  style.setAttribute('data-ahfu-keyframes', id);
  style.textContent = css;
  document.head.appendChild(style);
}
