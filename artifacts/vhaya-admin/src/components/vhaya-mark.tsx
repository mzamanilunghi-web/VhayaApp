type VhayaMarkProps = {
  className?: string;
};

export function VhayaMark({ className }: VhayaMarkProps) {
  return (
    <img
      className={className}
      src={`${import.meta.env.BASE_URL}favicon.svg`}
      alt=""
      aria-hidden="true"
    />
  );
}
