/** Stands in for a table that has nothing to show yet, and says why. */
export function EmptyBox({ message }: { message: string }) {
  return (
    <div className="rounded-xl border border-dashed border-slate-800 py-12 text-center">
      <p className="text-sm text-slate-500">{message}</p>
    </div>
  );
}
