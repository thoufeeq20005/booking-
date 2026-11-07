// Display-only price illustration in integer paise. Real charges are always calculated by the backend.

/** Platform fee for a fee in paise, using basis points so no floating-point rounding is involved. */
export function illustratePlatformFee(serviceFeePaise: number, commissionPercent: number) {
  const bps = Math.round(commissionPercent * 100);
  const platformFeePaise = Math.round((serviceFeePaise * bps) / 10_000);
  return { serviceFeePaise, platformFeePaise, totalPaise: serviceFeePaise + platformFeePaise };
}

export const paiseToRupees = (paise: number) => paise / 100;
