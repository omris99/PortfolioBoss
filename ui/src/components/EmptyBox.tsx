/** Stands in for a table that has nothing to show yet, and says why. */
export function EmptyBox({ message }: { message: string }) {
  return (
    <div className="rounded-lg border border-dashed border-strong bg-surface px-5 py-12 text-center">
      <p className="text-body text-muted">{message}</p>
    </div>
  );
}
