let injected = false;

export function injectTouchTarget(): void {
  if (typeof document === 'undefined' || injected) return;
  injected = true;
  const style = document.createElement('style');
  style.setAttribute('data-ahfu-utils', 'touch-target');
  style.textContent = `
.ahfu-touch-target {
  position: relative;
}
.ahfu-touch-target::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  min-width: 48px;
  min-height: 48px;
  width: 100%;
  height: 100%;
}
@media (pointer: fine) {
  .ahfu-touch-target::after {
    min-width: 40px;
    min-height: 40px;
  }
}
`;
  document.head.appendChild(style);
}
