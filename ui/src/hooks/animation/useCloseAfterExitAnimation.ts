import { useState, type AnimationEvent } from 'react';

/**
 * Lets a drawer or a modal leave with its exit animation (round 6's Motion) before the page takes it away. The page shows
 * it while it is open and removes it in `onClose`; removed at once, it would vanish without leaving. So ✕, Esc, a click
 * on the veil or a save that succeeded call `startClosing` instead: `isOpen` turns false, Radix plays the exit on the
 * element (its `data-state` is "closed"), and `closeWhenTheExitAnimationEnds` — the element's `onAnimationEnd` — calls
 * `onClose` once that animation is over.
 *
 * Every exit has an animation, a fade at least under reduce motion, so the end always comes.
 */
export function useCloseAfterExitAnimation(onClose: () => void) {
  const [isOpen, setIsOpen] = useState(true);

  const startClosing = () => setIsOpen(false);

  const closeWhenTheExitAnimationEnds = (event: AnimationEvent<HTMLElement>) => {
    // An animation inside the element ends here too (a tab's content fading in): only the element's own counts.
    if (!isOpen && event.target === event.currentTarget) onClose();
  };

  return { isOpen, startClosing, closeWhenTheExitAnimationEnds };
}
