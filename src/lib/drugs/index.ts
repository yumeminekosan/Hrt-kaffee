import { ESTROGENS } from './estrogens';
import { PROGESTOGENS, ANDROGENS } from './progestogens';
import { CYP3A4_FACTORS } from './interactions';

export const DRUG_DB = {
  ...ESTROGENS,
  ...PROGESTOGENS,
  ...ANDROGENS
};

export { ESTROGENS, PROGESTOGENS, ANDROGENS, CYP3A4_FACTORS };
export * from './types';
