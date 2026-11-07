import clsx from 'clsx';

export function Spinner({ size = 'md', className }: { size?: 'sm' | 'md' | 'lg'; className?: string }) {
  const dims = { sm: 'h-4 w-4 border-2', md: 'h-6 w-6 border-2', lg: 'h-10 w-10 border-[3px]' }[size];
  return (
    <span
      aria-hidden="true"
      className={clsx('inline-block animate-spin rounded-full border-current border-r-transparent', dims, className)}
    />
  );
}
