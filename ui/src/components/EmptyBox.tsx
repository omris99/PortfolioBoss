import { CARD_CLASS } from '../lib/styles';

/**
 * Stands in for a table that has nothing to show yet, and says why: a plain card with the reason in muted text. Not
 * dashed (round 6): a dashed outline is the app's sign for "add".
 */
export function EmptyBox({ message }: { message: string }) {
  return (
    <div className={`${CARD_CLASS} px-5 py-12 text-center`}>
      <p className="text-body text-muted">{message}</p>
    </div>
  );
}
